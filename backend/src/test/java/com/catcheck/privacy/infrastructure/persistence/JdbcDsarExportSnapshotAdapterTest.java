package com.catcheck.privacy.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcDsarExportSnapshotAdapterTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    private static JdbcTemplate jdbc;
    private static JdbcDsarExportSnapshotAdapter adapter;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        adapter = new JdbcDsarExportSnapshotAdapter(jdbc);
    }

    @Test
    void aiMessagesOfOneTurnSharingCreatedAtAreExportedUserThenAssistant() {
        UUID user = jdbc.queryForObject(
                "INSERT INTO app_user (email, full_name) VALUES ('dsar-ai@example.invalid', 'T') RETURNING id", UUID.class);
        Timestamp turn1 = Timestamp.from(Instant.parse("2026-10-10T08:00:00Z"));
        Timestamp turn2 = Timestamp.from(Instant.parse("2026-10-10T08:05:00Z"));
        UUID conversation = UUID.randomUUID();
        jdbc.update("INSERT INTO ai_conversation (id, user_id, created_at) VALUES (?, ?, ?)", conversation, user, turn1);
        insertMessage(conversation, "ASSISTANT", "a1", turn1);
        insertMessage(conversation, "USER", "q1", turn1);
        insertMessage(conversation, "ASSISTANT", "a2", turn2);
        insertMessage(conversation, "USER", "q2", turn2);

        List<String> contents = adapter.aiMessages(user).stream()
                .map(json -> json.replaceAll(".*\"content\":\"([^\"]*)\".*", "$1"))
                .toList();

        assertThat(contents).containsExactly("q1", "a1", "q2", "a2");
    }

    private static void insertMessage(UUID conversation, String role, String content, Timestamp createdAt) {
        jdbc.update("INSERT INTO ai_message (id, conversation_id, role, content, provider, created_at) VALUES (?, ?, ?, ?, 'test', ?)",
                UUID.randomUUID(), conversation, role, content, createdAt);
    }
}
