package com.shopsphere.demo;

import com.shopsphere.users.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Secure admin initialisation: creates the first administrator ONLY from ADMIN_EMAIL / ADMIN_PASSWORD environment
 * variables, and only if no admin exists yet. There is no default admin and no public endpoint that can create one.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private final UserStore users; private final PasswordEncoder encoder; private final String email, password;

    public AdminBootstrap(UserStore users, PasswordEncoder encoder, @Value("${shopsphere.admin-bootstrap.email}") String email, @Value("${shopsphere.admin-bootstrap.password}") String password) {
        this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }

    @Override public void run(ApplicationArguments args) {
        if (email.isBlank() || password.isBlank()) { log.info("No ADMIN_EMAIL/ADMIN_PASSWORD set: admin bootstrap skipped."); return; }
        if (users.adminExists()) { log.info("An administrator already exists: bootstrap skipped."); return; }
        if (password.length() < 10) throw new IllegalStateException("ADMIN_PASSWORD must be at least 10 characters.");
        if (users.findByEmail(email).isPresent()) throw new IllegalStateException("ADMIN_EMAIL belongs to an existing non-admin user. Use a different email.");
        users.createAdmin("Administrator", email, encoder.encode(password));
        log.info("Administrator account created for the configured ADMIN_EMAIL. Remove ADMIN_PASSWORD from the environment now.");
    }
}
