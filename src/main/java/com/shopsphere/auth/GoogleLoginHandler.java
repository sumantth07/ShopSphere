package com.shopsphere.auth;

import com.shopsphere.users.UserStore;
import com.shopsphere.users.UserStore.UserRow;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs after Spring Security has completed the OIDC code exchange and validated the ID token.
 * Identity key = Google "sub". Email alone NEVER links accounts: a matching email requires an emailed code.
 */
@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(GoogleLoginHandler.class);
    private final UserStore users;
    private final JdbcClient jdbc;
    private final AuthService auth;
    private final OtpService otp;
    private final EmailService email;
    private final String frontend;
    private final boolean secure;
    private final String sameSite;
    private final long refreshDays;

    public GoogleLoginHandler(UserStore users, JdbcClient jdbc, AuthService auth, OtpService otp, EmailService email,
                              @Value("${shopsphere.frontend-url}") String frontend, @Value("${shopsphere.cookie.secure}") boolean secure,
                              @Value("${shopsphere.cookie.same-site}") String sameSite, @Value("${shopsphere.jwt.refresh-ttl-days}") long refreshDays) {
        this.users = users; this.jdbc = jdbc; this.auth = auth; this.otp = otp; this.email = email;
        this.frontend = frontend; this.secure = secure; this.sameSite = sameSite; this.refreshDays = refreshDays;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest req, HttpServletResponse res, Authentication authentication) throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidc)) { res.sendRedirect(frontend + "/login?error=google_failed"); return; }
        if (req.getSession(false) != null) req.getSession(false).invalidate();   // the session was only needed for the OAuth handshake
        try {
            String sub = oidc.getSubject(), mail = UserStore.normalizeEmail(oidc.getEmail());
            if (mail == null || !Boolean.TRUE.equals(oidc.getEmailVerified())) { res.sendRedirect(frontend + "/login?error=google_unverified"); return; }

            var linked = jdbc.sql("select user_id from user_identities where provider = 'GOOGLE' and subject = :s").param("s", sub).query(Long.class).optional();
            UserRow user;
            if (linked.isPresent()) {
                user = users.findById(linked.get()).orElseThrow();
            } else {
                var existing = users.findByEmail(mail);
                if (existing.isPresent()) {
                    // Same email, different login method: do NOT merge silently. Prove mailbox control first.
                    var issued = otp.issue(existing.get().id(), "GOOGLE_LINK", sub, true);
                    email.send(mail, "Confirm linking Google to ShopSphere",
                        "Someone signed in with Google using your email. To link Google to your existing ShopSphere account, enter this code: " + issued.code()
                        + "\nIt expires in " + otp.ttl().toMinutes() + " minutes. If this was not you, ignore this email: nothing will be linked.");
                    res.sendRedirect(frontend + "/auth/link?token=" + URLEncoder.encode(issued.linkToken(), StandardCharsets.UTF_8));
                    return;
                }
                String name = oidc.getFullName() != null ? oidc.getFullName() : mail;
                long id = users.create(name, mail, null, true);
                jdbc.sql("insert into user_identities (user_id, provider, subject, email) values (:u, 'GOOGLE', :s, :e)").param("u", id).param("s", sub).param("e", mail).update();
                user = users.findById(id).orElseThrow();
            }
            var session = auth.startSession(user);
            res.addHeader(HttpHeaders.SET_COOKIE, AuthController.refreshCookie(session.refreshToken(), Duration.ofDays(refreshDays), secure, sameSite).toString());
            res.sendRedirect(frontend + "/auth/callback");   // frontend then calls /api/auth/refresh: no token ever appears in a URL
        } catch (Exception e) {
            log.warn("Google login failed: {}", e.getClass().getSimpleName());
            res.sendRedirect(frontend + "/login?error=google_failed");
        }
    }

    public void onFailure(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex) throws IOException {
        res.sendRedirect(frontend + "/login?error=google_failed");
    }
}
