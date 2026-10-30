package com.shopsphere.auth;

import com.shopsphere.auth.AuthDtos.*;
import com.shopsphere.exception.ApiException;
import com.shopsphere.security.JwtService;
import com.shopsphere.users.UserStore;
import com.shopsphere.users.UserStore.UserRow;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    public record Session(AuthResponse body, String refreshToken) {}

    private final UserStore users;
    private final OtpService otp;
    private final EmailService email;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final JdbcClient jdbc;
    private final int refreshDays;

    public AuthService(UserStore users, OtpService otp, EmailService email, PasswordEncoder encoder, JwtService jwt, JdbcClient jdbc,
                       @Value("${shopsphere.jwt.refresh-ttl-days}") int refreshDays) {
        this.users = users; this.otp = otp; this.email = email; this.encoder = encoder; this.jwt = jwt; this.jdbc = jdbc; this.refreshDays = refreshDays;
    }

    // ---------- registration & verification ----------
    @Transactional
    public void register(RegisterRequest r) {
        if (!r.password().equals(r.confirmPassword())) throw ApiException.badRequest("PASSWORD_MISMATCH", "Passwords do not match.");
        var existing = users.findByEmail(r.email());
        long id;
        if (existing.isPresent()) {
            UserRow u = existing.get();
            if (u.emailVerified()) throw ApiException.conflict("EMAIL_EXISTS", "An account with this email already exists. Try signing in.");
            users.updateRegistration(u.id(), r.fullName().trim(), encoder.encode(r.password())); // unverified: let the owner retry
            id = u.id();
        } else {
            id = users.create(r.fullName().trim(), r.email(), encoder.encode(r.password()), false);
        }
        sendVerification(id, UserStore.normalizeEmail(r.email()));
    }

    private void sendVerification(long userId, String to) {
        var issued = otp.issue(userId, "EMAIL_VERIFY", null, false);
        email.send(to, "Verify your ShopSphere email",
            "Your ShopSphere verification code is " + issued.code() + ".\nIt expires in " + otp.ttl().toMinutes() + " minutes. If you did not sign up, ignore this email.");
    }

    public void resendVerification(String emailAddr) {
        users.findByEmail(emailAddr).filter(u -> !u.emailVerified() && "ACTIVE".equals(u.status())).ifPresent(u -> sendVerification(u.id(), u.email()));
        // Same response whether or not the account exists: no account enumeration.
    }

    @Transactional(noRollbackFor = ApiException.class) // keep attempt counters / re-sent codes even when we then reject
    public Session verifyEmail(VerifyRequest r) {
        UserRow u = users.findByEmail(r.email()).orElseThrow(() -> ApiException.badRequest("CODE_INVALID", "That code is incorrect."));
        otp.verify(u.id(), "EMAIL_VERIFY", r.code());
        users.markVerified(u.id());
        return startSession(users.findById(u.id()).orElseThrow());
    }

    // ---------- login / tokens ----------
    @Transactional(noRollbackFor = ApiException.class) // keep attempt counters / re-sent codes even when we then reject
    public Session login(LoginRequest r) {
        UserRow u = users.findByEmail(r.email()).orElse(null);
        if (u == null || u.passwordHash() == null || !encoder.matches(r.password(), u.passwordHash()))
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Incorrect email or password.");
        if (!u.emailVerified()) {
            try { sendVerification(u.id(), u.email()); } catch (ApiException ignored) { /* cooldown: a code was just sent */ }
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email. We sent you a code.");
        }
        return startSession(u);
    }

    /** Issues tokens. Refuses unverified or non-active accounts, so token holders are always verified. */
    @Transactional
    public Session startSession(UserRow u) {
        if (!"ACTIVE".equals(u.status())) throw ApiException.forbidden("ACCOUNT_SUSPENDED", "This account is not active.");
        if (!u.emailVerified()) throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Please verify your email first.");
        users.touchLogin(u.id());
        String refresh = OtpService.newToken();
        jdbc.sql("insert into refresh_tokens (user_id, token_hash, expires_at) values (:u, :h, :e)")
            .param("u", u.id()).param("h", OtpService.hashToken(refresh)).param("e", OffsetDateTime.now().plusDays(refreshDays)).update();
        return new Session(new AuthResponse(jwt.issueAccessToken(u.id(), u.role()), jwt.ttlSeconds(), userDto(u.id())), refresh);
    }

    /** Rotates the refresh token. Re-use of a revoked token revokes every session of that user (theft signal). */
    @Transactional(noRollbackFor = ApiException.class)
    public Session refresh(String token) {
        if (token == null || token.isBlank()) throw ApiException.unauthorized("NO_REFRESH_TOKEN", "Not signed in.");
        var row = jdbc.sql("select id, user_id, revoked_at, expires_at from refresh_tokens where token_hash = :h for update")
            .param("h", OtpService.hashToken(token))
            .query((rs, n) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getObject(3, OffsetDateTime.class), rs.getObject(4, OffsetDateTime.class)}).optional();
        if (row.isEmpty()) throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        Object[] r = row.get();
        long userId = (Long) r[1];
        if (r[2] != null) { revokeAll(userId); throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again."); }
        if (((OffsetDateTime) r[3]).isBefore(OffsetDateTime.now())) throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired. Please sign in again.");
        jdbc.sql("update refresh_tokens set revoked_at = now() where id = :id").param("id", r[0]).update();
        UserRow u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Session expired."));
        return startSession(u);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank())
            jdbc.sql("update refresh_tokens set revoked_at = now() where token_hash = :h and revoked_at is null").param("h", OtpService.hashToken(token)).update();
    }

    public void revokeAll(long userId) {
        jdbc.sql("update refresh_tokens set revoked_at = now() where user_id = :u and revoked_at is null").param("u", userId).update();
    }

    // ---------- password reset (also how a Google-created account gets a local password) ----------
    public void forgotPassword(String emailAddr) {
        users.findByEmail(emailAddr).filter(u -> "ACTIVE".equals(u.status())).ifPresent(u -> {
            try {
                var issued = otp.issue(u.id(), "PASSWORD_RESET", null, false);
                email.send(u.email(), "Reset your ShopSphere password",
                    "Your password reset code is " + issued.code() + ". It expires in " + otp.ttl().toMinutes() + " minutes. If you did not request this, ignore this email.");
            } catch (ApiException cooldown) { /* silently ignore to avoid enumeration */ }
        });
    }

    @Transactional(noRollbackFor = ApiException.class) // keep attempt counters / re-sent codes even when we then reject
    public void resetPassword(ResetRequest r) {
        UserRow u = users.findByEmail(r.email()).orElseThrow(() -> ApiException.badRequest("CODE_INVALID", "That code is incorrect."));
        otp.verify(u.id(), "PASSWORD_RESET", r.code());
        users.setPassword(u.id(), encoder.encode(r.newPassword()));
        users.markVerified(u.id());   // they just proved control of the mailbox
        revokeAll(u.id());
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest r) {
        UserRow u = users.findById(userId).orElseThrow();
        if (u.passwordHash() == null)
            throw ApiException.badRequest("USE_FORGOT_PASSWORD", "This account signs in with Google. Use 'Forgot password' to set a password by email.");
        if (r.currentPassword() == null || !encoder.matches(r.currentPassword(), u.passwordHash()))
            throw ApiException.badRequest("INVALID_CREDENTIALS", "Current password is incorrect.");
        users.setPassword(userId, encoder.encode(r.newPassword()));
        revokeAll(userId);
    }

    // ---------- Google account linking ----------
    @Transactional(noRollbackFor = ApiException.class) // keep attempt counters / re-sent codes even when we then reject
    public Session confirmGoogleLink(ConfirmLinkRequest r) {
        long userId = otp.userForLinkToken(r.token()).orElseThrow(() -> ApiException.badRequest("LINK_EXPIRED", "This link request has expired. Start again with Google."));
        var verified = otp.verify(userId, "GOOGLE_LINK", r.code());
        UserRow u = users.findById(userId).orElseThrow();
        boolean subjectTaken = jdbc.sql("select exists(select 1 from user_identities where provider = 'GOOGLE' and subject = :s)").param("s", verified.extSubject()).query(Boolean.class).single();
        if (subjectTaken) throw ApiException.conflict("IDENTITY_EXISTS", "This Google account is already linked to a ShopSphere account.");
        boolean alreadyHasGoogle = jdbc.sql("select exists(select 1 from user_identities where provider = 'GOOGLE' and user_id = :u)").param("u", userId).query(Boolean.class).single();
        if (alreadyHasGoogle) throw ApiException.conflict("ALREADY_LINKED", "A different Google account is already linked to this account.");
        if (!u.emailVerified()) { users.clearPassword(userId); users.markVerified(userId); } // squatter protection: an unverified local account's password is discarded
        jdbc.sql("insert into user_identities (user_id, provider, subject, email) values (:u, 'GOOGLE', :s, :e)").param("u", userId).param("s", verified.extSubject()).param("e", u.email()).update();
        return startSession(users.findById(userId).orElseThrow());
    }

    public UserDto userDto(long id) {
        return jdbc.sql("""
            select u.id, u.full_name, u.email, u.email_verified, u.role, (u.password_hash is not null) as has_password,
                   exists(select 1 from user_identities i where i.user_id = u.id and i.provider = 'GOOGLE') as google_linked,
                   (select s.application_status from sellers s where s.user_id = u.id) as seller_status
            from users u where u.id = :id""").param("id", id)
            .query((rs, n) -> new UserDto(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getString(5),
                rs.getBoolean(6), rs.getBoolean(7), rs.getString(8))).single();
    }
}
