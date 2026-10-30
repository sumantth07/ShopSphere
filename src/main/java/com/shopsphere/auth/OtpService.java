package com.shopsphere.auth;

import com.shopsphere.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Types;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates and verifies emailed one-time codes. Codes are stored only as peppered SHA-256 hashes. */
@Service
public class OtpService {
    public record Issued(String code, String linkToken) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcClient jdbc;
    private final String pepper;
    private final int ttlMinutes, maxAttempts, cooldownSeconds;

    public OtpService(JdbcClient jdbc, @Value("${shopsphere.jwt.secret}") String pepper,
                      @Value("${shopsphere.otp.ttl-minutes}") int ttl, @Value("${shopsphere.otp.max-attempts}") int max,
                      @Value("${shopsphere.otp.resend-cooldown-seconds}") int cooldown) {
        this.jdbc = jdbc; this.pepper = pepper; this.ttlMinutes = ttl; this.maxAttempts = max; this.cooldownSeconds = cooldown;
    }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    String hashCode(long userId, String purpose, String code) { return sha256(pepper + ":" + userId + ":" + purpose + ":" + code); }
    public static String newCode() { return String.format("%06d", RANDOM.nextInt(1_000_000)); }
    public static String newToken() { byte[] b = new byte[32]; RANDOM.nextBytes(b); return HexFormat.of().formatHex(b); }
    public static String hashToken(String token) { return sha256(token); }

    /** Enforces resend cooldown, invalidates older codes, stores a new hashed code. */
    @Transactional
    public Issued issue(long userId, String purpose, String extSubject, boolean withLinkToken) {
        Optional<OffsetDateTime> last = jdbc.sql("select max(created_at) from one_time_codes where user_id = :u and purpose = :p")
            .param("u", userId).param("p", purpose).query(OffsetDateTime.class).optional();
        if (last.isPresent() && last.get().isAfter(OffsetDateTime.now().minusSeconds(cooldownSeconds)))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_COOLDOWN", "Please wait a minute before requesting another code.");
        jdbc.sql("update one_time_codes set consumed_at = now() where user_id = :u and purpose = :p and consumed_at is null")
            .param("u", userId).param("p", purpose).update();
        String code = newCode();
        String token = withLinkToken ? newToken() : null;
        jdbc.sql("""
            insert into one_time_codes (user_id, purpose, code_hash, expires_at, link_token_hash, ext_subject)
            values (:u, :p, :h, :exp, :lt, :sub)""")
            .param("u", userId).param("p", purpose).param("h", hashCode(userId, purpose, code))
            .param("exp", OffsetDateTime.now().plusMinutes(ttlMinutes))
            .param("lt", token == null ? null : hashToken(token), Types.CHAR).param("sub", extSubject, Types.VARCHAR).update();
        return new Issued(code, token);
    }

    public record Verified(long codeId, String extSubject) {}

    /** Verifies the latest live code for (user, purpose). Counts failed attempts and locks after the maximum. */
    @Transactional(noRollbackFor = ApiException.class)
    public Verified verify(long userId, String purpose, String code) {
        var row = jdbc.sql("""
            select id, code_hash, attempts, expires_at, ext_subject from one_time_codes
            where user_id = :u and purpose = :p and consumed_at is null order by created_at desc limit 1 for update""")
            .param("u", userId).param("p", purpose).query((rs, n) -> new Object[]{rs.getLong(1), rs.getString(2), rs.getInt(3),
                rs.getObject(4, OffsetDateTime.class), rs.getString(5)}).optional();
        if (row.isEmpty()) throw ApiException.badRequest("CODE_EXPIRED", "This code has expired or was already used. Request a new one.");
        Object[] r = row.get();
        long id = (Long) r[0];
        if (((OffsetDateTime) r[3]).isBefore(OffsetDateTime.now()))
            throw ApiException.badRequest("CODE_EXPIRED", "This code has expired. Request a new one.");
        if ((Integer) r[2] >= maxAttempts)
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS", "Too many incorrect attempts. Request a new code.");
        boolean ok = MessageDigest.isEqual(((String) r[1]).getBytes(StandardCharsets.UTF_8),
            hashCode(userId, purpose, code == null ? "" : code.trim()).getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            jdbc.sql("update one_time_codes set attempts = attempts + 1 where id = :id").param("id", id).update();
            throw ApiException.badRequest("CODE_INVALID", "That code is incorrect.");
        }
        jdbc.sql("update one_time_codes set consumed_at = now() where id = :id").param("id", id).update();
        return new Verified(id, (String) r[4]);
    }

    /** Resolves the user id behind a Google-link token (the token itself is only ever stored hashed). */
    public Optional<Long> userForLinkToken(String token) {
        return jdbc.sql("select user_id from one_time_codes where link_token_hash = :h and consumed_at is null and purpose = 'GOOGLE_LINK'")
            .param("h", hashToken(token)).query(Long.class).optional();
    }

    public Duration ttl() { return Duration.ofMinutes(ttlMinutes); }
}
