package com.shopsphere.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived access tokens. Claims: sub (user id), role, iss, aud, iat, exp. No PII, no secrets. */
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final String issuer;
    private final String audience;
    private final Duration ttl;

    public JwtService(JwtEncoder encoder, @Value("${shopsphere.jwt.issuer}") String issuer,
                      @Value("${shopsphere.jwt.audience}") String audience,
                      @Value("${shopsphere.jwt.access-ttl-minutes}") long ttlMinutes) {
        this.encoder = encoder; this.issuer = issuer; this.audience = audience; this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public String issueAccessToken(long userId, String role) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).audience(List.of(audience)).issuedAt(now)
            .expiresAt(now.plus(ttl)).subject(String.valueOf(userId)).claim("role", role).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    public long ttlSeconds() { return ttl.toSeconds(); }
}
