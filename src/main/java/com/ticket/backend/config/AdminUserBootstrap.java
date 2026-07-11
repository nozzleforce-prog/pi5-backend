package com.ticket.backend.config;

import com.ticket.backend.model.User;
import com.ticket.backend.model.UserRole;
import com.ticket.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the bootstrap ADMIN user from env when that username does not exist yet.
 */
@Component
@Order(1)
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminUserBootstrap(
            UserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            @Value("${app.bootstrap.admin.username:admin}") String username,
            @Value("${app.bootstrap.admin.password:admin}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (username == null || username.isBlank()) {
            log.warn("Bootstrap admin skipped: app.bootstrap.admin.username is blank");
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Bootstrap admin skipped: app.bootstrap.admin.password is blank");
            return;
        }

        String normalized = username.trim();
        if (userRepository.findByUsername(normalized).isPresent()) {
            log.info("Bootstrap admin already exists: {}", normalized);
            return;
        }

        User admin = new User();
        admin.setUsername(normalized);
        admin.setPassword(passwordEncoder.encode(password));
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        log.info("Bootstrap ADMIN user created: {}", normalized);
    }
}
