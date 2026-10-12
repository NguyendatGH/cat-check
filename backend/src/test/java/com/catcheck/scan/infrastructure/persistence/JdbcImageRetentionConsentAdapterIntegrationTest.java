package com.catcheck.scan.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcImageRetentionConsentAdapterIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private JdbcImageRetentionConsentAdapter adapter;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD));
        adapter = new JdbcImageRetentionConsentAdapter(jdbc);
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Scan image consent test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "scan-consent-" + UUID.randomUUID() + "@example.test");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM consent_record WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void latestRecordDecidesAndNoRecordMeansNotGranted() {
        assertThat(adapter.isGranted(userId)).as("chua co dong nao => chua dong y").isFalse();

        record("GRANTED", "now() - interval '2 minutes'");
        assertThat(adapter.isGranted(userId)).isTrue();

        record("WITHDRAWN", "now() - interval '1 minute'");
        assertThat(adapter.isGranted(userId)).as("rut consent la mot dong moi hon").isFalse();
    }

    private void record(String status, String occurredAtSql) {
        jdbc.update("""
                INSERT INTO consent_record (id, user_id, purpose_code, status, policy_version_id,
                                            policy_hash, consent_text_hash, method, occurred_at)
                SELECT uuidv7(), ?, 'SCAN_IMAGE_RETAIN', ?, pv.id, repeat('a', 64), repeat('b', 64),
                       'WEB_TOGGLE', %s
                  FROM policy_version pv
                 LIMIT 1
                """.formatted(occurredAtSql), userId, status);
    }
}
