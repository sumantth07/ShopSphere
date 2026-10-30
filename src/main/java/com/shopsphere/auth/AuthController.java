package com.shopsphere.auth;

import com.shopsphere.auth.AuthDtos.*;
import com.shopsphere.auth.AuthService.Session;
import com.shopsphere.exception.ApiException;
import com.shopsphere.security.CurrentUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    static final String COOKIE = "ss_refresh";
    private final AuthService auth;
    private final boolean secure, googleEnabled;
    private final String sameSite;
    private final long refreshDays;

    public AuthController(AuthService auth, @Value("${shopsphere.cookie.secure}") boolean secure, @Value("${shopsphere.cookie.same-site}") String sameSite,
                          @Value("${shopsphere.jwt.refresh-ttl-days}") long refreshDays, @Value("${shopsphere.google-enabled}") boolean googleEnabled) {
        this.auth = auth; this.secure = secure; this.sameSite = sameSite; this.refreshDays = refreshDays; this.googleEnabled = googleEnabled;
    }

    public static ResponseCookie refreshCookie(String value, Duration maxAge, boolean secure, String sameSite) {
        return ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure).sameSite(sameSite).path("/api/auth").maxAge(maxAge).build();
    }

    private ResponseEntity<AuthResponse> withSession(Session s) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie(s.refreshToken(), Duration.ofDays(refreshDays), secure, sameSite).toString()).body(s.body());
    }

    @GetMapping("/providers")
    public Map<String, Boolean> providers() { return Map.of("google", googleEnabled); }

    @PostMapping("/register")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest r) {
        auth.register(r);
        return new MessageResponse("Account created. Enter the 6-digit code we emailed you.");
    }

    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verify(@Valid @RequestBody VerifyRequest r) { return withSession(auth.verifyEmail(r)); }

    @PostMapping("/resend-verification")
    public MessageResponse resend(@Valid @RequestBody EmailRequest r) {
        auth.resendVerification(r.email());
        return new MessageResponse("If the account exists and is unverified, a new code has been sent.");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest r) { return withSession(auth.login(r)); }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest req) {
        if (!"ShopSphere".equals(req.getHeader("X-Requested-With"))) throw ApiException.forbidden("CSRF", "Missing required header."); // blocks simple cross-site form posts
        return withSession(auth.refresh(readCookie(req)));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(HttpServletRequest req) {
        auth.logout(readCookie(req));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO, secure, sameSite).toString()).body(new MessageResponse("Signed out."));
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgot(@Valid @RequestBody EmailRequest r) {
        auth.forgotPassword(r.email());
        return new MessageResponse("If an account exists for that email, we sent a reset code.");
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetRequest r) {
        auth.resetPassword(r);
        return new MessageResponse("Password updated. You can sign in now.");
    }

    @PostMapping("/google/confirm-link")
    public ResponseEntity<AuthResponse> confirmLink(@Valid @RequestBody ConfirmLinkRequest r) { return withSession(auth.confirmGoogleLink(r)); }

    @PostMapping("/change-password")
    public MessageResponse change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(CurrentUser.id(jwt), r);
        return new MessageResponse("Password changed. Other sessions were signed out.");
    }

    private static String readCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        for (Cookie c : req.getCookies()) if (COOKIE.equals(c.getName())) return c.getValue();
        return null;
    }
}
