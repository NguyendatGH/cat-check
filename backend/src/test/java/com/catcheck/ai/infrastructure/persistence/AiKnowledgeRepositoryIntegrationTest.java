package com.catcheck.ai.infrastructure.persistence;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SQL của {@link AiKnowledgeRepository} chạy trên Postgres local thật (không Testcontainers/Docker —
 * cùng cách {@code JdbcShopRepositoryCheckoutRaceIntegrationTest}).
 */
class AiKnowledgeRepositoryIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private AiKnowledgeRepository repository;
    private UUID subjectId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD));
        repository = new AiKnowledgeRepository(jdbc);
        subjectId = insertUser("ai-repo-subject-");
        otherUserId = insertUser("ai-repo-other-");
    }

    @AfterEach
    void cleanUp() {
        for (UUID userId : List.of(subjectId, otherUserId)) {
            jdbc.update("DELETE FROM ai_conversation WHERE user_id = ?", userId); // ai_message: ON DELETE CASCADE
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }

    /**
     * Hai tin của MỘT lượt hỏi-đáp được lưu trong cùng transaction ({@code AiChatService#chat}) nên
     * có cùng {@code created_at = now()} (DB local: 18 tin chỉ có 9 mốc); {@code id} là UUIDv4 ngẫu
     * nhiên, bảng không có cột tăng dần nào khác. Trước bản sửa, {@code ORDER BY created_at DESC}
     * để thứ tự hỏi/đáp trong một lượt phụ thuộc thứ tự vật lý/plan (đổi sau UPDATE, VACUUM FULL,
     * khôi phục dữ liệu, hoặc khi planner chọn seq scan + sort). Test cố ý ghi ASSISTANT trước USER
     * về vật lý để chứng minh truy vấn không tự định nghĩa thứ tự.
     */
    @Test
    void historyKeepsQuestionBeforeAnswerWhenBothShareTheSameTimestamp() {
        UUID conversation = repository.createConversation(subjectId, null).id();
        OffsetDateTime firstTurn = OffsetDateTime.of(2026, 10, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        insertTurnAnswerFirst(conversation, firstTurn, "q1", "a1");
        insertTurnAnswerFirst(conversation, firstTurn.plusMinutes(1), "q2", "a2");

        assertThat(repository.history(conversation, 10)).extracting(row -> row[0] + ":" + row[1])
                .containsExactly("USER:q1", "ASSISTANT:a1", "USER:q2", "ASSISTANT:a2");
        assertThat(repository.history(conversation, 2)).extracting(row -> row[0] + ":" + row[1])
                .containsExactly("USER:q2", "ASSISTANT:a2");
    }

    private void insertTurnAnswerFirst(UUID conversationId, OffsetDateTime at, String question, String answer) {
        String sql = "INSERT INTO ai_message(id, conversation_id, role, content, provider, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, UUID.randomUUID(), conversationId, "ASSISTANT", answer, "retrieval-fallback", at);
        jdbc.update(sql, UUID.randomUUID(), conversationId, "USER", question, "user", at);
    }

    @Test
    void erasesQuestionAndAnswerContentOfTheSubjectOnlyAndIsIdempotent() {
        UUID subjectConversation = repository.createConversation(subjectId, null).id();
        repository.saveMessage(subjectConversation, "USER", "Email tôi là an@example.test, mèo Mun bị gì?", "user");
        repository.saveMessage(subjectConversation, "ASSISTANT", "Mun đang có cảnh báo mức khẩn cấp:", "emergency-template");
        UUID otherConversation = repository.createConversation(otherUserId, null).id();
        repository.saveMessage(otherConversation, "USER", "Câu hỏi của người khác", "user");

        int erased = repository.eraseConversationContent(subjectId);

        assertThat(erased).isEqualTo(2);
        assertThat(contents(subjectConversation)).containsOnly(AiKnowledgeRepository.ERASED_CONTENT).hasSize(2);
        assertThat(contents(otherConversation)).containsExactly("Câu hỏi của người khác");
        assertThat(repository.eraseConversationContent(subjectId)).isZero();
    }

    private List<String> contents(UUID conversationId) {
        return jdbc.queryForList("SELECT content FROM ai_message WHERE conversation_id = ? ORDER BY created_at",
                String.class, conversationId);
    }

    private UUID insertUser(String emailPrefix) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'AI repository test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, emailPrefix + UUID.randomUUID() + "@example.test");
    }
}
