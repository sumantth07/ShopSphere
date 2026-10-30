package com.shopsphere.config;

import com.shopsphere.auth.GoogleLoginHandler;
import com.shopsphere.security.RateLimitFilter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecretKey jwtKey(@Value("${shopsphere.jwt.secret}") String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
            throw new IllegalStateException("JWT_SECRET must be set to at least 32 characters (e.g. `openssl rand -base64 48`).");
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean
    JwtDecoder jwtDecoder(SecretKey key, @Value("${shopsphere.jwt.issuer}") String issuer, @Value("${shopsphere.jwt.audience}") String audience) {
        NimbusJwtDecoder d = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> aud = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(audience)
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Wrong audience", null));
        d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), aud)); // checks exp, nbf, iss
        return d;
    }

    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, GoogleLoginHandler google) throws Exception {
        JwtAuthenticationConverter conv = new JwtAuthenticationConverter();
        conv.setJwtGrantedAuthoritiesConverter(jwt -> List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));

        http.csrf(AbstractHttpConfigurer::disable) // Bearer-token API. The one cookie endpoint (/api/auth/refresh) requires a custom header + SameSite.
            .cors(c -> {})
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)) // session only used for the Google OAuth handshake
            .addFilterBefore(new RateLimitFilter(), BasicAuthenticationFilter.class)
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.GET, "/api/products/**", "/api/categories/**", "/api/brands", "/uploads/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/chatbot/message").permitAll()   // anonymous allowed; private data needs a valid token
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/oauth2/**", "/error").permitAll()
                .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(conv))
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "UNAUTHENTICATED", "Your session has expired or is invalid. Please sign in again."))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "FORBIDDEN", "You do not have access to this resource.")))
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "UNAUTHENTICATED", "Please sign in."))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "FORBIDDEN", "You do not have access to this resource.")))
            .oauth2Login(o -> o.successHandler(google).failureHandler(google::onFailure));
        return http.build();
    }

    private static void json(jakarta.servlet.http.HttpServletResponse res, int status, String code, String msg) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/json");
        res.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\",\"message\":\"" + msg + "\"}");
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${shopsphere.cors.allowed-origins}") List<String> origins) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        cfg.setAllowCredentials(true); // needed for the HttpOnly refresh cookie when frontend and API are on different origins
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }
}
