package com.pfe.config;

import com.pfe.MainPackage.Entity.*;
import com.pfe.MainPackage.Entity.AccountStatus;
import com.pfe.MainPackage.Entity.AppUser;
import com.pfe.MainPackage.Entity.UserRole;
import com.pfe.MainPackage.Repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class SeedAdminRunner implements CommandLineRunner {

    private final UserRepository repo;
    private final PasswordEncoder encoder;

    public SeedAdminRunner(UserRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {

        String adminEmail = "admin@local.tn";

        // ✅ create admin only if not exists
        if (repo.existsByEmail(adminEmail)) return;

        AppUser admin = AppUser.builder()
                .fullName("System Admin")
                .email(adminEmail)
                .password(encoder.encode("admin123"))

                // ✅ IMPORTANT for new approval workflow
                .role(UserRole.ROLE_ADMIN)
                .roleRequested(null)
                .status(AccountStatus.APPROVED)
                .enabled(true)

                .build();

        repo.save(admin);

        System.out.println("✅ Admin seeded: admin@local.tn / admin123");
    }
}