package com.pfe.config;

import com.pfe.MainPackage.Entity.*;
import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Dev/test convenience only: never runs when app.jwt.secret's real
// counterpart matters, i.e. never in production (see @Profile below).
// Production must create its first admin through a real, audited flow.
@Component
@Profile("!prod")
public class SeedAdminRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedAdminRunner.class);

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    @Value("${app.seed-admin.email:admin@local.tn}")
    private String adminEmail;

    @Value("${app.seed-admin.password:admin123}")
    private String adminPassword;

    public SeedAdminRunner(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        if (repo.existsByEmail(adminEmail)) return;

        AppUser admin = AppUser.builder()
                .fullName("System Admin")
                .email(adminEmail)
                .password(encoder.encode(adminPassword))
                .role(UserRole.ROLE_ADMIN)
                .roleRequested(null)
                .status(AccountStatus.APPROVED)
                .enabled(true)
                .build();

        repo.save(admin);

        log.info("Seeded default admin account ({}) for local development/testing.", adminEmail);
    }
}