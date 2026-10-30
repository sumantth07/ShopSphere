package com.shopsphere.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

/**
 * DEVELOPMENT ONLY. Used when no SMTP host is configured so you can test OTP flows locally.
 * It prints the whole email (including the one-time code) to the log, which is exactly what must never
 * happen in production, so the app refuses to start with this mailer in the "prod" profile.
 */
@Service
@ConditionalOnExpression("'${spring.mail.host:}'.isEmpty()")
public class DevConsoleEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(DevConsoleEmailService.class);

    public DevConsoleEmailService(Environment env) {
        if (env.acceptsProfiles(Profiles.of("prod")))
            throw new IllegalStateException("MAIL_HOST is not configured. The dev console mailer is not allowed in the prod profile.");
        log.warn("No MAIL_HOST configured: emails (including OTP codes) will be PRINTED TO THE LOG. Development only.");
    }

    @Override public void send(String to, String subject, String body) {
        log.warn("\n===== DEV EMAIL =====\nTo: {}\nSubject: {}\n{}\n=====================", to, subject, body);
    }
}
