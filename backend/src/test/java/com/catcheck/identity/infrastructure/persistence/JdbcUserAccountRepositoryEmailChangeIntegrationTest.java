package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.UserIdentity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcUserAccountRepositoryEmailChangeIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";

    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, "catcheck", "catcheck_local_only");
        try (Connection ignored = dataSource.getConnection()) {
        } catch (Exception ex) {
            Assumptions.abort("PostgreSQL local khong san sang: " + ex.getClass().getSimpleName());
        }
        jdbc = new JdbcTemplate(dataSource);
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Test
    void emailChangeReleasesTheOldAddressInTheLocalIdentity() {
        JdbcUserAccountRepository accounts = new JdbcUserAccountRepository(jdbc);
        JdbcUserIdentityRepository identities = new JdbcUserIdentityRepository(jdbc);
        String oldEmail = "email-change-old-" + UUID.randomUUID() + "@example.test";
        String newEmail = "email-change-new-" + UUID.randomUUID() + "@example.test";
        Instant now = Instant.now();

        tx.executeWithoutResult(status -> {
            status.setRollbackOnly();
            UUID userId = jdbc.queryForObject("""
                    INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                    VALUES (?, 'Email change test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                    RETURNING id
                    """, UUID.class, oldEmail);
            identities.insert(new UserIdentity(UUID.randomUUID(), userId, IdentityProvider.LOCAL,
                    oldEmail, oldEmail, true, "hash", now, null, now, now));

            accounts.updateEmail(userId, newEmail, now);

            assertThat(identities.findLocal(userId)).get()
                    .extracting(UserIdentity::providerUserId, UserIdentity::providerEmail)
                    .containsExactly(newEmail, newEmail);
            assertThat(identities.existsByProviderSubject(IdentityProvider.LOCAL, oldEmail)).isFalse();
        });
    }
}
