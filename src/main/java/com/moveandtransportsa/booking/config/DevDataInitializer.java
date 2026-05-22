package com.moveandtransportsa.booking.config;

import com.moveandtransportsa.booking.domain.auth.AdminRole;
import com.moveandtransportsa.booking.domain.auth.AdminUser;
import com.moveandtransportsa.booking.repository.AdminUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Ensures a default SUPER_ADMIN user exists on startup in local/dev environments.
 * Safe to run multiple times — skips if the user already exists.
 *
 * Credentials: admin / Admin@1234
 */
@Slf4j
@Component
@Profile("!prod")
@RequiredArgsConstructor
public class DevDataInitializer implements ApplicationRunner {

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.findByUsername("admin").isEmpty()) {
            AdminUser admin = AdminUser.builder()
                    .username("admin")
                    .displayName("System Administrator")
                    .passwordHash(passwordEncoder.encode("Admin@1234"))
                    .role(AdminRole.SUPER_ADMIN)
                    .active(true)
                    .build();
            adminUserRepository.save(admin);
            log.info("Dev admin user created: admin / Admin@1234");
        } else {
            // Update the password hash to ensure it matches the current encoder config
            adminUserRepository.findByUsername("admin").ifPresent(user -> {
                user.setPasswordHash(passwordEncoder.encode("Admin@1234"));
                adminUserRepository.save(user);
                log.info("Dev admin password hash refreshed for user: admin");
            });
        }
    }
}
