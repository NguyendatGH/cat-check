package com.catcheck.ai.infrastructure.persistence;

import com.catcheck.ai.application.AiChatService;
import com.catcheck.ai.application.AiKnowledgePort;
import com.catcheck.ai.application.AiText;
import com.catcheck.shared.testing.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class AiKnowledgeRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    // Locale riêng của test để không lẫn với care tip seed (vi).
    private static final String TEST_LOCALE = "en";
    private static final String MODEL = "test-model";

    private static JdbcTemplate jdbc;
    private AiKnowledgeRepository repository;
    private final List<UUID> careTipIds = new ArrayList<>();
    private UUID subjectId;
    private UUID otherUserId;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load()
                .migrate();
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(dataSource);
    }

    @BeforeEach
    void setUp() {
        repository = new AiKnowledgeRepository(jdbc);
        subjectId = insertUser("ai-repo-subject-");
        otherUserId = insertUser("ai-repo-other-");
    }

    @AfterEach
    void cleanUp() {
        for (UUID userId : List.of(subjectId, otherUserId)) {
            jdbc.update("DELETE FROM ai_conversation WHERE user_id = ?", userId);
            jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
        }
        for (UUID careTipId : careTipIds) {
            jdbc.update("DELETE FROM ai_citation WHERE document_id = ?", documentIdOf(careTipId));
            jdbc.update("DELETE FROM ai_document WHERE source_type = 'CARE_TIP' AND source_id = ?", careTipId);
            jdbc.update("DELETE FROM care_tip WHERE id = ?", careTipId);
        }
    }

    @Test
    void resyncIsIncrementalAndOnlyChangedChunksLoseTheirEmbedding() {
        String longBody = paragraphs("Dọn khay", 3) + "\n\n" + paragraphs("Thay cát", 3);
        UUID tip = careTip("PUBLISHED", "Vệ sinh khay cát", longBody);
        repository.syncPublishedCareTips();
        embedAllWith(MODEL);
        assertThat(repository.syncPublishedCareTips().changedDocuments()).as("nothing changed").isZero();

        Map<Integer, String> before = chunkHashes(tip);
        assertThat(before).hasSizeGreaterThan(1);
        jdbc.update("UPDATE care_tip SET body_md = ? WHERE id = ?",
                longBody.replace("Thay cát đoạn 3", "Thay cát đoạn ba (đã sửa)"), tip);
        AiKnowledgePort.SyncResult resync = repository.syncPublishedCareTips();

        assertThat(resync.changedDocuments()).isEqualTo(1);
        Map<Integer, String> after = chunkHashes(tip);
        int changed = (int) before.keySet().stream().filter(ordinal -> !before.get(ordinal).equals(after.get(ordinal))).count();
        assertThat(changed).isPositive().isLessThan(before.size());
        assertThat(repository.chunksNeedingEmbedding(MODEL, 100)).hasSize(changed);
    }

    @Test
    void archivedCareTipIsDeactivatedAndNeverRetrieved() {
        UUID published = careTip("PUBLISHED", "Khay zqxkhay hằng ngày", "Dọn zqxkhay zqxcat hằng ngày cho sạch.");
        UUID archived = careTip("ARCHIVED", "Khay zqxkhay cũ", "Dọn zqxkhay zqxcat theo cách cũ.");
        repository.syncPublishedCareTips();

        assertThat(lexical("zqxkhay zqxcat")).containsExactly(documentIdOf(published));
        assertThat(lexical("zqxkhay zqxcat")).doesNotContain(documentIdOf(archived));
    }

    @Test
    void questionsWithoutDiacriticsOrWithSmallTyposStillFindTheDocument() {
        UUID tip = careTip("PUBLISHED", "Vệ sinh khay cát hằng ngày",
                "Dọn cát bẩn hằng ngày. Thay toàn bộ lớp cát theo thời gian phù hợp.");
        repository.syncPublishedCareTips();

        assertThat(lexical("Cách vệ sinh khay cát thế nào?")).containsExactly(documentIdOf(tip));
        assertThat(lexical("cach ve sinh khay cat the nao")).containsExactly(documentIdOf(tip));
        assertThat(lexical("khayy hangg")).containsExactly(documentIdOf(tip));
    }

    @Test
    void offTopicQuestionSharingOnlyOneIncidentalWordReturnsNoChunk() {
        careTip("PUBLISHED", "Vệ sinh khay cát hằng ngày",
                "Dọn cát bẩn hằng ngày. Thay toàn bộ lớp cát theo thời gian phù hợp.");
        repository.syncPublishedCareTips();

        assertThat(lexical("hôm nay thời tiết thế nào")).isEmpty();
        assertThat(lexical("bạn tên là gì")).isEmpty();
    }

    @Test
    void vectorBranchRanksBySimilarityAndIgnoresVectorsFromAnotherModel() {
        UUID near = careTip("PUBLISHED", "Uống nước", "Đặt bát nước sạch ở nhiều nơi.");
        UUID far = careTip("PUBLISHED", "Chải lông", "Chải lông mỗi tuần.");
        UUID otherModel = careTip("PUBLISHED", "Ánh sáng", "Chụp ảnh nơi đủ sáng.");
        repository.syncPublishedCareTips();
        setEmbedding(near, unit(0), MODEL);
        setEmbedding(far, unit(1), MODEL);
        setEmbedding(otherModel, unit(0), "old-model");

        List<AiKnowledgePort.RetrievedChunk> hits = repository.searchCandidates(
                TEST_LOCALE, List.of(), unit(0), MODEL, 20).vector();

        assertThat(hits).extracting(AiKnowledgePort.RetrievedChunk::documentId)
                .containsExactly(documentIdOf(near), documentIdOf(far));
        assertThat(hits.getFirst().score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(hits.getFirst().chunkId()).isNotNull();
        assertThat(hits.getFirst().chunkHash()).isNotNull();
    }

    @Test
    void sameRequestKeyIsAcceptedOnceAndTheStoredReplyIsReplayedOnlyToItsOwner() {
        UUID tip = careTip("PUBLISHED", "Vệ sinh khay cát", "Dọn cát bẩn hằng ngày.");
        repository.syncPublishedCareTips();
        AiKnowledgePort.RetrievedChunk chunk = repository.searchCandidates(
                TEST_LOCALE, AiText.contentTerms("vệ sinh khay cát"), null, MODEL, 5).lexical().getFirst().withRank(1);
        UUID conversation = repository.createConversation(subjectId, null).id();
        UUID key = UUID.randomUUID();

        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).isPresent();
        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).isEmpty();
        assertThat(repository.findReply(subjectId, key)).as("question stored, answer not yet").isEmpty();

        UUID answerId = repository.saveAssistantMessage(conversation, "Trả lời [1]", "openai-compatible", key);
        repository.saveCitations(answerId, List.of(chunk));

        AiChatService.ChatResult replay = repository.findReply(subjectId, key).orElseThrow();
        assertThat(replay.messageId()).isEqualTo(answerId);
        assertThat(replay.answer()).isEqualTo("Trả lời [1]");
        assertThat(replay.citations()).extracting(AiChatService.Citation::title).containsExactly("Vệ sinh khay cát");
        assertThat(repository.findReply(otherUserId, key)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT chunk_hash FROM ai_citation WHERE message_id = ?", String.class, answerId))
                .isEqualTo(chunk.chunkHash());
        assertThat(documentIdOf(tip)).isEqualTo(chunk.documentId());
    }

    @Test
    void releasedOrStaleUnansweredQuestionFreesItsRequestKeyButAFreshOrAnsweredOneKeepsIt() {
        UUID conversation = repository.createConversation(subjectId, null).id();
        UUID key = UUID.randomUUID();

        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).isPresent();
        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).as("fresh turn still in progress").isEmpty();

        backdateMessagesOf(key);
        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).as("stale orphan released").isPresent();

        repository.releaseRequestKey(key);
        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).as("released after a failed turn").isPresent();

        repository.saveAssistantMessage(conversation, "Trả lời", "retrieval-fallback", key);
        backdateMessagesOf(key);
        assertThat(repository.saveUserMessage(conversation, "Câu hỏi", key)).as("answered turn keeps its key").isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_message WHERE conversation_id = ? AND role = 'USER'",
                Integer.class, conversation)).isEqualTo(3);
    }

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

    @Test
    void erasesQuestionAndAnswerContentOfTheSubjectOnlyAndIsIdempotent() {
        UUID subjectConversation = repository.createConversation(subjectId, null).id();
        repository.saveUserMessage(subjectConversation, "Email tôi là an@example.test, mèo Mun bị gì?", null);
        repository.saveAssistantMessage(subjectConversation, "Mun đang có cảnh báo mức khẩn cấp:", "emergency-template", null);
        UUID otherConversation = repository.createConversation(otherUserId, null).id();
        repository.saveUserMessage(otherConversation, "Câu hỏi của người khác", null);

        int erased = repository.eraseConversationContent(subjectId);

        assertThat(erased).isEqualTo(2);
        assertThat(contents(subjectConversation)).containsOnly(AiKnowledgeRepository.ERASED_CONTENT).hasSize(2);
        assertThat(contents(otherConversation)).containsExactly("Câu hỏi của người khác");
        assertThat(repository.eraseConversationContent(subjectId)).isZero();
    }

    private List<UUID> lexical(String question) {
        return repository.searchCandidates(TEST_LOCALE, AiText.contentTerms(question), null, MODEL, 6).lexical()
                .stream().map(AiKnowledgePort.RetrievedChunk::documentId).distinct().toList();
    }

    private UUID careTip(String status, String title, String body) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO care_tip (slug, locale, title, body_md, status, claim_type)
                VALUES (?, ?, ?, ?, ?, 'NONE') RETURNING id
                """, UUID.class, "ai-repo-test-" + UUID.randomUUID(), TEST_LOCALE, title, body, status);
        careTipIds.add(id);
        return id;
    }

    private static String paragraphs(String prefix, int count) {
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            if (i > 1) text.append("\n\n");
            text.append(prefix).append(" đoạn ").append(i).append(": ").append("nội dung hướng dẫn chi tiết. ".repeat(20));
        }
        return text.toString();
    }

    private Map<Integer, String> chunkHashes(UUID careTipId) {
        Map<Integer, String> hashes = new java.util.HashMap<>();
        jdbc.query("SELECT ordinal, content_hash FROM ai_chunk WHERE document_id = ?",
                rs -> { hashes.put(rs.getInt(1), rs.getString(2)); }, documentIdOf(careTipId));
        return hashes;
    }

    private void embedAllWith(String model) {
        Map<UUID, float[]> vectors = new java.util.HashMap<>();
        repository.chunksNeedingEmbedding(model, 1000).forEach(chunk -> vectors.put(chunk.id(), unit(0)));
        repository.saveEmbeddings(model, vectors);
    }

    private void setEmbedding(UUID careTipId, float[] vector, String model) {
        List<UUID> chunkIds = jdbc.queryForList("SELECT id FROM ai_chunk WHERE document_id = ?", UUID.class,
                documentIdOf(careTipId));
        Map<UUID, float[]> vectors = new java.util.HashMap<>();
        chunkIds.forEach(id -> vectors.put(id, vector));
        repository.saveEmbeddings(model, vectors);
    }

    private static float[] unit(int axis) {
        float[] vector = new float[384];
        vector[axis] = 1f;
        return vector;
    }

    private static UUID documentIdOf(UUID careTipId) {
        return UUID.nameUUIDFromBytes(("care_tip:" + careTipId + ":" + TEST_LOCALE).getBytes(StandardCharsets.UTF_8));
    }

    private void insertTurnAnswerFirst(UUID conversationId, OffsetDateTime at, String question, String answer) {
        String sql = "INSERT INTO ai_message(id, conversation_id, role, content, provider, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, UUID.randomUUID(), conversationId, "ASSISTANT", answer, "retrieval-fallback", at);
        jdbc.update(sql, UUID.randomUUID(), conversationId, "USER", question, "user", at);
    }

    private void backdateMessagesOf(UUID requestKey) {
        jdbc.update("UPDATE ai_message SET created_at = now() - INTERVAL '3 minutes' WHERE request_key = ?", requestKey);
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
