package com.catcheck.identity.infrastructure.demo;

import com.catcheck.identity.domain.port.PasswordHasher;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("local")
public class DemoAccountSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Admin@123";

    record DemoAccount(String email, String fullName, Set<String> roles) { }

    static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount("demo@catcheck.vn", "Chu nuôi demo", Set.of("USER")),
            new DemoAccount("seller@catcheck.vn", "Seller demo", Set.of("USER", "ADMIN_CATALOG")),
            new DemoAccount("admin@catcheck.vn", "Admin hệ thống demo", Set.of("USER", "ADMIN_SUPER")));

    private final JdbcTemplate jdbc;
    private final PasswordHasher passwordHasher;

    public DemoAccountSeeder(JdbcTemplate jdbc, PasswordHasher passwordHasher) {
        this.jdbc = jdbc;
        this.passwordHasher = passwordHasher;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (DemoAccount account : ACCOUNTS) {
            UUID userId = findOrCreateUser(account);
            ensureLocalIdentity(userId, account.email());
            replaceRoles(userId, account.roles());
        }
    }

    private UUID findOrCreateUser(DemoAccount account) {
        List<UUID> existing = jdbc.queryForList(
                "SELECT id FROM app_user WHERE email = ?", UUID.class, account.email());
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        return jdbc.queryForObject(
                "INSERT INTO app_user (email, full_name, status, onboarding_status, email_verified_at)"
                        + " VALUES (?, ?, 'ACTIVE', 'COMPLETED', now()) RETURNING id",
                UUID.class, account.email(), account.fullName());
    }

    private void ensureLocalIdentity(UUID userId, String email) {
        List<String> storedHashes = jdbc.queryForList(
                "SELECT password_hash FROM user_identity WHERE user_id = ? AND provider = 'LOCAL'",
                String.class, userId);
        if (storedHashes.isEmpty()) {
            jdbc.update(
                    "INSERT INTO user_identity (user_id, provider, provider_user_id, provider_email,"
                            + " email_verified, password_hash) VALUES (?, 'LOCAL', ?, ?, true, ?)",
                    userId, email, email, passwordHasher.hash(DEMO_PASSWORD));
            return;
        }
        String stored = storedHashes.get(0);
        if (stored == null || !passwordHasher.matches(DEMO_PASSWORD, stored)) {
            jdbc.update(
                    "UPDATE user_identity SET password_hash = ?, updated_at = now()"
                            + " WHERE user_id = ? AND provider = 'LOCAL'",
                    passwordHasher.hash(DEMO_PASSWORD), userId);
        }
    }

    private void replaceRoles(UUID userId, Set<String> roles) {
        jdbc.update("DELETE FROM user_role WHERE user_id = ?", userId);
        for (String role : roles) {
            jdbc.update("INSERT INTO user_role (user_id, role) VALUES (?, ?)", userId, role);
        }
    }
}
