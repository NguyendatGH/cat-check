package com.catcheck.ai.application;

import com.catcheck.ai.api.AiErrorCode;
import com.catcheck.ai.application.spi.AccountStatus;
import com.catcheck.ai.application.spi.UserAccountPort;
import com.catcheck.ai.domain.AiChatRateLimitRule;
import com.catcheck.ai.domain.port.AiChatRateLimiter;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.testing.NoOpTransactionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiChatServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID CAT_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-04T07:00:00Z");

    private static final String EXPECTED_EMERGENCY_BODY =
            "⚠️ Những dấu hiệu cần đi thú y NGAY — đừng chờ theo dõi thêm\n\n"
            + "Hãy đưa bé đến cơ sở thú y ngay lập tức nếu bạn thấy:\n"
            + "- Bé rặn nhiều nhưng không ra nước tiểu, hoặc chỉ ra vài giọt\n"
            + "- Bé kêu, gào, tỏ ra đau khi đi vệ sinh\n"
            + "- Bé ra vào khay liên tục mà khay vẫn khô\n"
            + "- Nước tiểu có máu\n"
            + "- Bé bỏ ăn, nôn, nằm li bì, bụng căng cứng\n\n"
            + "Tắc nghẽn đường tiểu là tình trạng cấp cứu và có thể nguy hiểm đến tính mạng trong vòng 24–48 giờ. "
            + "Trong những trường hợp này, đừng dùng CatCheck để chờ theo dõi thêm — hãy gọi cho phòng khám thú y gần nhất.";

    @Test
    void retrievesContextPersistsConversationAndFallsBackWhenProviderUnavailable() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = false;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi màu pH", "/care-tips/ph", "Theo dõi màu dưới ánh sáng ổn định.", 1));

        AiChatService.ChatResult result = service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, null, null, "Làm sao theo dõi màu pH?", "vi");

        assertThat(result.conversationId()).isNotNull();
        assertThat(result.answer()).contains("Theo tài liệu CatCheck");
        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        assertThat(result.citations()).extracting(AiChatService.Citation::title)
                .containsExactly("Theo dõi màu pH");
        assertThat(knowledge.synced).as("indexing happens outside the request path").isFalse();
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
        assertThat(knowledge.savedCitationMessageId).isNotNull();
    }

    @Test
    void sendsConversationHistoryAndProviderAnswerIntoTheSameConversation() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi pH", "/care-tips/ph", "Đo dưới ánh sáng ổn định.", 1));
        knowledge.history = List.of(new String[]{"USER", "Câu hỏi trước"}, new String[]{"ASSISTANT", "Trả lời trước"});
        List<Map<String, String>> sentMessages = new ArrayList<>();
        UUID requestedConversation = UUID.randomUUID();
        AiChatService.ChatResult result = service(knowledge, messages -> {
            sentMessages.addAll(messages);
            return Optional.of("Câu trả lời có căn cứ [1]");
        }).chat(USER_ID, requestedConversation, null, "Câu hỏi mới", "vi");

        assertThat(result.conversationId()).isEqualTo(requestedConversation);
        assertThat(result.answer()).isEqualTo("Câu trả lời có căn cứ [1]");
        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(sentMessages).extracting(message -> message.get("role"))
                .containsExactly("system", "user", "assistant", "user");
        assertThat(sentMessages.getLast().get("content")).contains("Câu hỏi mới");
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    @Test
    void redactsContactPiiFromCurrentPromptHistoryAndPersistedQuestion() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Chăm sóc mèo", "/care-tips/care", "Theo dõi sức khỏe định kỳ.", 1));
        knowledge.history = java.util.Collections.singletonList(
                new String[]{"USER", "Liên hệ meo@example.test hoặc 0912 345 678"});
        List<Map<String, String>> sentMessages = new ArrayList<>();
        String question = "Email của tôi là an@example.test, số 0987654321; mèo cần chăm sóc gì?";

        service(knowledge, messages -> {
            sentMessages.addAll(messages);
            return Optional.of("Hãy theo dõi sức khỏe định kỳ [1].");
        }).chat(USER_ID, UUID.randomUUID(), null, question, "vi");

        String providerText = sentMessages.stream().map(message -> message.get("content"))
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(providerText).doesNotContain("meo@example.test", "0912 345 678", "an@example.test", "0987654321");
        assertThat(providerText).contains("[đã ẩn email]", "[đã ẩn số điện thoại]");
        String persisted = String.join("\n", knowledge.savedContents);
        assertThat(persisted).doesNotContain("an@example.test", "0987654321");
        assertThat(knowledge.savedContents.getFirst()).contains("[đã ẩn email]", "[đã ẩn số điện thoại]");
    }

    @Test
    void emergencySignInTheQuestionShortCircuitsWithoutCatIdAndWithoutCallingTheProvider() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay cát hằng ngày", "/care-tips/ve-sinh", "Dọn cát bẩn hằng ngày.", 1));
        CountingProvider provider = new CountingProvider();

        AiChatService.ChatResult result = service(knowledge, provider)
                .chat(USER_ID, null, null, "mèo rặn mãi không ra nước tiểu từ sáng nay, gọi tôi 0987654321", "vi");

        assertThat(result.provider()).isEqualTo("emergency-template");
        assertThat(result.answer()).isEqualTo(EXPECTED_EMERGENCY_BODY);
        assertThat(result.citations()).isEmpty();
        assertThat(provider.calls).isZero();
        assertThat(knowledge.searchedLocales).isEmpty();
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
        assertThat(knowledge.savedContents.getFirst()).doesNotContain("0987654321");
    }

    @Test
    void ordinaryQuestionIsNotTreatedAsAnEmergency() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay cát hằng ngày", "/care-tips/ve-sinh", "Dọn cát bẩn hằng ngày.", 1));
        CountingProvider provider = new CountingProvider();

        AiChatService.ChatResult result = service(knowledge, provider)
                .chat(USER_ID, null, null, "Cách vệ sinh khay cát thế nào?", "vi");

        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        assertThat(result.answer()).doesNotContain("cần đi thú y NGAY");
        assertThat(provider.calls).isEqualTo(1);
        assertThat(knowledge.searchedLocales).containsExactly("vi");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mèo rặn nhiều mà chỉ ra vài giọt",
            "meo ran mai khong ra nuoc tieu tu sang nay",
            "Bé kêu đau khi đi vệ sinh",
            "bé ra vào khay liên tục mà khay vẫn khô",
            "Nước tiểu có máu thì sao?",
            "Mèo BỎ   ĂN hai hôm nay",
            "meo bo an 2 ngay",
            "Bé nôn từ tối qua",
            "bé nằm li bì cả ngày",
            "bụng căng cứng khi sờ"})
    void approvedEmergencySignsInTheQuestionTriggerTheEmergencyTemplate(String question) {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        CountingProvider provider = new CountingProvider();

        AiChatService.ChatResult result = service(knowledge, provider).chat(USER_ID, null, null, question, "vi");

        assertThat(result.provider()).isEqualTo("emergency-template");
        assertThat(provider.calls).isZero();
        assertThat(knowledge.searchedLocales).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Mèo non nên ăn gì?",
            "Nước tiểu có màu vàng đậm có sao không?",
            "Cho mèo ăn cá rán được không?",
            "pH nước tiểu bao nhiêu là ổn?"})
    void questionsThatOnlyLookLikeEmergencySignsWithoutDiacriticsAreNotEmergencies(String question) {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;

        AiChatService.ChatResult result = service(knowledge, new CountingProvider())
                .chat(USER_ID, null, null, question, "vi");

        assertThat(result.provider()).isNotEqualTo("emergency-template");
        assertThat(knowledge.searchedLocales).containsExactly("vi");
    }

    /** M6: provider LLM bên ngoài phải được gọi NGOÀI mọi transaction DB. */
    @Test
    void providerIsCalledOutsideAnyDatabaseTransaction() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay cát hằng ngày", "/care-tips/ve-sinh", "Dọn cát bẩn hằng ngày.", 1));
        TrackingTransactionManager txManager = new TrackingTransactionManager();
        List<Integer> openTransactionsAtProviderCall = new ArrayList<>();
        AiProviderPort provider = messages -> {
            openTransactionsAtProviderCall.add(txManager.open);
            return Optional.of("Dọn khay mỗi ngày [1].");
        };
        AiChatService service = new AiChatService(knowledge, retrieval(knowledge), provider, new AiRuntimeConfig(6, 8),
                new FakeCatContextPort(), new FakeAiChatRateLimiter(), userId -> AccountStatus.ACTIVE,
                realMessageResolver(), Clock.fixed(NOW, ZoneOffset.UTC), txManager);

        AiChatService.ChatResult result = service.chat(USER_ID, null, null, "Cách vệ sinh khay cát thế nào?", "vi");

        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(openTransactionsAtProviderCall).containsExactly(0);
        assertThat(txManager.begun).isEqualTo(2);
        assertThat(txManager.open).isZero();
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    private static final class TrackingTransactionManager extends NoOpTransactionManager {
        int open;
        int begun;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            open++;
            begun++;
            return super.getTransaction(definition);
        }

        @Override
        public void commit(TransactionStatus status) {
            open--;
        }

        @Override
        public void rollback(TransactionStatus status) {
            open--;
        }
    }

    private static final class CountingProvider implements AiProviderPort {
        int calls;

        @Override
        public Optional<String> complete(List<Map<String, String>> messages) {
            calls++;
            return Optional.empty();
        }
    }

    @Test
    void fallsBackToVietnameseKnowledgeWhenRequestedLocaleHasNoPublishedDocuments() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.onlyVietnamese = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Cách chụp ảnh khay cát", "/care-tips/photo", "Dọn khay trước khi chụp.", 1));

        AiChatService.ChatResult result = service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, null, null, "How do I take a litter photo?", "en");

        assertThat(knowledge.searchedLocales).containsExactly("en", "vi");
        assertThat(result.citations()).extracting(AiChatService.Citation::title).containsExactly("Cách chụp ảnh khay cát");
    }

    @Test
    void systemPromptTellsTheModelToAnswerInTheRequestLanguage() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi pH", "/care-tips/ph", "Đo dưới ánh sáng ổn định.", 1));
        List<String> systemPrompts = new ArrayList<>();
        AiProviderPort provider = messages -> {
            systemPrompts.add(messages.getFirst().get("content"));
            return Optional.of("Câu trả lời [1]");
        };

        service(knowledge, provider).chat(USER_ID, null, null, "Cách đo pH?", "vi");
        service(knowledge, provider).chat(USER_ID, null, null, "How do I measure pH?", "en");

        assertThat(systemPrompts).hasSize(2);
        assertThat(systemPrompts.get(0)).contains("tiếng Việt").doesNotContain("English");
        assertThat(systemPrompts.get(1)).contains("Always answer in English").doesNotContain("Luôn trả lời");
    }

    @Test
    void rejectsConversationOwnedByAnotherUser() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.conversation = new AiKnowledgePort.Conversation(CONVERSATION_ID, UUID.randomUUID());

        assertThatThrownBy(() -> service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, CONVERSATION_ID, null, "Xin chào", "vi"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesWithoutCallingProviderWhenRetrievalHasNoEvidence() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        AiProviderPort provider = mock(AiProviderPort.class);

        AiChatService.ChatResult result = service(knowledge, provider)
                .chat(USER_ID, null, null, "Mèo nhà tôi thích đồ chơi nào?", "vi");

        assertThat(result.answer()).contains("chưa tìm thấy tài liệu");
        assertThat(result.citations()).isEmpty();
        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        verifyNoInteractions(provider);
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }


    @Test
    void urgentHealthFlagShortCircuitsToTheVerbatimEmergencyTemplateWithoutCallingTheProvider() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Chăm sóc mèo", "/care-tips/care", "Nội dung không liên quan.", 1));
        FakeCatContextPort catContextPort = new FakeCatContextPort();
        catContextPort.contextToReturn = new CatContextPort.CatContext(
                CAT_ID, "Mun", 14, "DSH", null, "MALE", true,
                new BigDecimal("4.2"), NOW.minusSeconds(86_400),
                null, null,
                List.of(new CatContextPort.OpenHealthFlag("URGENT_CLINICAL_SIGN", "URGENT", NOW)),
                List.of(), null, List.of(), null);
        AiProviderPort provider = mock(AiProviderPort.class);

        AiChatService.ChatResult result = service(knowledge, provider, catContextPort)
                .chat(USER_ID, null, CAT_ID, "Mun đang có vẻ khó chịu", "vi");

        assertThat(result.provider()).isEqualTo("emergency-template");
        assertThat(result.citations()).isEmpty();
        assertThat(result.answer()).startsWith("Mun đang có cảnh báo mức khẩn cấp:");
        assertThat(result.answer()).contains(EXPECTED_EMERGENCY_BODY);
        assertThat(result.answer()).contains("/cats/" + CAT_ID);
        verifyNoInteractions(provider);
        assertThat(knowledge.searchedLocales).isEmpty();
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
        assertThat(knowledge.savedCitationMessageId).isNotNull();
    }

    @Test
    void nonUrgentCatContextIsRenderedIntoThePromptSentToTheProvider() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi pH", "/care-tips/ph", "Đo dưới ánh sáng ổn định.", 1));
        FakeCatContextPort catContextPort = new FakeCatContextPort();
        catContextPort.contextToReturn = new CatContextPort.CatContext(
                CAT_ID, "Mun", 14, "DSH", null, "MALE", true,
                new BigDecimal("4.2"), NOW.minusSeconds(86_400),
                new CatContextPort.LatestScanSummary(NOW, new BigDecimal("6.8"), "SLIGHTLY_HIGH",
                        new BigDecimal("0.6"), "CARD_CCM", false),
                null, List.of(), List.of(), null, List.of(), null);
        List<Map<String, String>> sentMessages = new ArrayList<>();

        AiChatService.ChatResult result = service(knowledge, messages -> {
            sentMessages.addAll(messages);
            return Optional.of("Theo dữ liệu gần nhất, pH của Mun hơi cao [1].");
        }, catContextPort).chat(USER_ID, null, CAT_ID, "pH của Mun tuần này thế nào?", "vi");

        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(catContextPort.lastUserId).isEqualTo(USER_ID);
        assertThat(catContextPort.lastCatId).isEqualTo(CAT_ID);
        String userPrompt = sentMessages.getLast().get("content");
        assertThat(userPrompt).contains("THÔNG TIN BÉ MÈO:");
        assertThat(userPrompt).contains("pH 6.8", "SLIGHTLY_HIGH", "Mun");
        assertThat(userPrompt).doesNotContain("nên cho", "có thể bị", "nên đưa");
    }

    @Test
    void fallbackAnswerNeverLeaksRawCatContextIntoTheUserFacingText() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay cát hằng ngày", "/care-tips/ve-sinh",
                "Dọn cát bẩn hằng ngày, thay toàn bộ lớp cát theo chu kỳ.", 1));
        FakeCatContextPort catContextPort = new FakeCatContextPort();
        catContextPort.contextToReturn = new CatContextPort.CatContext(
                CAT_ID, "Bo", 38, "DSH", null, "FEMALE", false,
                new BigDecimal("3.1"), NOW.minusSeconds(86_400),
                new CatContextPort.LatestScanSummary(NOW, new BigDecimal("6.5"), "IN_RANGE",
                        new BigDecimal("0.8"), "NONE", false),
                null, List.of(), List.of(), null, List.of(), null);
        AiProviderPort provider = messages -> Optional.empty();

        AiChatService.ChatResult result = service(knowledge, provider, catContextPort)
                .chat(USER_ID, null, CAT_ID, "Cách vệ sinh khay cát thế nào?", "vi");

        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        assertThat(result.answer()).doesNotContain("THÔNG TIN BÉ MÈO", "Bo", "3.1", "Lần quét gần nhất");
        assertThat(result.answer()).contains("Dọn cát bẩn hằng ngày");
    }

    @Test
    void catIdNotOwnedByCallerIsTreatedAsNoCatIdWithoutError() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi pH", "/care-tips/ph", "Đo dưới ánh sáng ổn định.", 1));
        FakeCatContextPort catContextPort = new FakeCatContextPort();
        catContextPort.contextToReturn = null;
        List<Map<String, String>> sentMessages = new ArrayList<>();

        AiChatService.ChatResult result = service(knowledge, messages -> {
            sentMessages.addAll(messages);
            return Optional.of("Câu trả lời chung, không có ngữ cảnh mèo [1].");
        }, catContextPort).chat(USER_ID, null, CAT_ID, "pH tuần này thế nào?", "vi");

        assertThat(result.answer()).isEqualTo("Câu trả lời chung, không có ngữ cảnh mèo [1].");
        assertThat(sentMessages.getLast().get("content")).doesNotContain("THÔNG TIN BÉ MÈO");
        assertThat(catContextPort.called).isTrue();
    }

    @Test
    void rateLimitedUserIsRejectedBeforeTouchingRepositoryOrProvider() {
        FakeKnowledge knowledge = new FakeKnowledge();
        FakeAiChatRateLimiter limiter = new FakeAiChatRateLimiter();
        limiter.blocked = true;
        limiter.retryAfterSeconds = 42;
        AiProviderPort provider = mock(AiProviderPort.class);

        assertThatThrownBy(() -> service(knowledge, provider, new FakeCatContextPort(), limiter)
                .chat(USER_ID, null, null, "Xin chào", "vi"))
                .isInstanceOf(RateLimitedException.class)
                .extracting(ex -> ((RateLimitedException) ex).retryAfterSeconds())
                .isEqualTo(42L);

        verifyNoInteractions(provider);
        assertThat(knowledge.searchedLocales).isEmpty();
        assertThat(limiter.consumedSubjects).containsExactly(USER_ID.toString());
    }

    @Test
    void citationNumbersFollowThePositionUsedForTheMarkersNotTheSqlRank() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(
                new AiKnowledgePort.RetrievedChunk(UUID.randomUUID(), "Nguồn A", "/care-tips/a", "Nội dung A.", 2),
                new AiKnowledgePort.RetrievedChunk(UUID.randomUUID(), "Nguồn B", "/care-tips/b", "Nội dung B.", 1));
        List<Map<String, String>> sentMessages = new ArrayList<>();

        AiChatService.ChatResult result = service(knowledge, messages -> {
            sentMessages.addAll(messages);
            return Optional.of("Theo nguồn A [1].");
        }).chat(USER_ID, null, null, "Câu hỏi", "vi");

        assertThat(sentMessages.getLast().get("content")).contains("[1] Nguồn A", "[2] Nguồn B");
        assertThat(result.citations()).extracting(AiChatService.Citation::title, AiChatService.Citation::rank)
                .containsExactly(tuple("Nguồn A", 1), tuple("Nguồn B", 2));
        assertThat(knowledge.savedCitationRanks).containsExactly(1, 2);
    }

    @Test
    void restrictedAccountIsRejectedBeforeAnyMessageIsStoredOrTheProviderIsCalled() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi pH", "/care-tips/ph", "Đo dưới ánh sáng ổn định.", 1));
        AiProviderPort provider = mock(AiProviderPort.class);
        FakeAiChatRateLimiter limiter = new FakeAiChatRateLimiter();

        assertThatThrownBy(() -> service(knowledge, provider, new FakeCatContextPort(), limiter,
                userId -> AccountStatus.RESTRICTED).chat(USER_ID, null, null, "pH bao nhiêu là ổn?", "vi"))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(ex -> ((PermissionDeniedException) ex).errorCode())
                .isEqualTo(AiErrorCode.ACCOUNT_RESTRICTED);

        verifyNoInteractions(provider);
        assertThat(knowledge.savedRoles).isEmpty();
        assertThat(knowledge.searchedLocales).isEmpty();
        assertThat(limiter.consumedSubjects).isEmpty();
    }

    @Test
    void unknownAccountIsRejectedLikeRestricted() {
        FakeKnowledge knowledge = new FakeKnowledge();
        AiProviderPort provider = mock(AiProviderPort.class);

        assertThatThrownBy(() -> service(knowledge, provider, new FakeCatContextPort(),
                new FakeAiChatRateLimiter(), userId -> null).chat(USER_ID, null, null, "Xin chào", "vi"))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(provider);
        assertThat(knowledge.savedRoles).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Mèo bị bệnh gì vậy?", "Liều lượng thuốc cho mèo 4kg?", "cho meo uong thuoc gi",
            "Bạn chẩn đoán giúp mình"})
    void diagnosisOrDosageRequestsGetTheRefusalTemplateWithoutRetrievalOrProvider(String question) {
        FakeKnowledge knowledge = new FakeKnowledge();
        CountingProvider provider = new CountingProvider();

        AiChatService.ChatResult result = service(knowledge, provider).chat(USER_ID, null, null, question, "vi");

        assertThat(result.provider()).isEqualTo("refusal-template");
        assertThat(result.answer()).contains("bác sĩ thú y");
        assertThat(provider.calls).isZero();
        assertThat(knowledge.searchedLocales).isEmpty();
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Không có trích dẫn nào.", "Theo nguồn [3].", "Theo nguồn [1] và [0]."})
    void generatedAnswerWithoutValidCitationsIsReplacedByTheRetrievalFallback(String generated) {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(
                new AiKnowledgePort.RetrievedChunk(UUID.randomUUID(), "Nguồn A", "/care-tips/a", "Nội dung A.", 1),
                new AiKnowledgePort.RetrievedChunk(UUID.randomUUID(), "Nguồn B", "/care-tips/b", "Nội dung B.", 2));

        AiChatService.ChatResult result = service(knowledge, messages -> Optional.of(generated))
                .chat(USER_ID, null, null, "Câu hỏi về khay cát", "vi");

        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        assertThat(result.answer()).doesNotContain(generated).contains("Theo tài liệu CatCheck");
    }

    @Test
    void retryWithTheSameRequestKeyReplaysTheAnswerWithoutAnotherProviderCallOrRateLimitHit() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay", "/care-tips/ve-sinh", "Dọn cát hằng ngày.", 1));
        FakeAiChatRateLimiter limiter = new FakeAiChatRateLimiter();
        int[] calls = {0};
        AiChatService service = service(knowledge, messages -> {
            calls[0]++;
            return Optional.of("Dọn cát hằng ngày [1].");
        }, new FakeCatContextPort(), limiter);
        UUID key = UUID.randomUUID();

        AiChatService.ChatResult first = service.chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key);
        AiChatService.ChatResult retry = service.chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key);

        assertThat(retry.messageId()).isEqualTo(first.messageId());
        assertThat(retry.answer()).isEqualTo(first.answer());
        assertThat(calls[0]).isEqualTo(1);
        assertThat(limiter.consumedSubjects).hasSize(1);
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    @Test
    void duplicateRequestStillInProgressIsRejectedBeforeCallingTheProvider() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay", "/care-tips/ve-sinh", "Dọn cát hằng ngày.", 1));
        UUID key = UUID.randomUUID();
        knowledge.usedRequestKeys.add(AiChatService.scopedRequestKey(USER_ID, key)); // câu hỏi đã ghi, câu trả lời chưa có
        AiProviderPort provider = mock(AiProviderPort.class);

        assertThatThrownBy(() -> service(knowledge, provider)
                .chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key))
                .isInstanceOf(com.catcheck.shared.error.ConflictException.class)
                .extracting(ex -> ((com.catcheck.shared.error.ConflictException) ex).errorCode())
                .isEqualTo(AiErrorCode.AI_REQUEST_IN_PROGRESS);
        verifyNoInteractions(provider);
    }

    @Test
    void failedTurnReleasesTheRequestKeySoARetryWithTheSameKeyRunsAgain() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay", "/care-tips/ve-sinh", "Dọn cát hằng ngày.", 1));
        int[] calls = {0};
        AiChatService service = service(knowledge, messages -> {
            if (calls[0]++ == 0) {
                throw new IllegalStateException("provider down");
            }
            return Optional.of("Dọn cát hằng ngày [1].");
        });
        UUID key = UUID.randomUUID();

        assertThatThrownBy(() -> service.chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key))
                .isInstanceOf(IllegalStateException.class);
        AiChatService.ChatResult retry = service.chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key);

        assertThat(retry.provider()).isEqualTo("openai-compatible");
        assertThat(calls[0]).isEqualTo(2);
        assertThat(knowledge.savedRoles).containsExactly("USER", "USER", "ASSISTANT");
    }

    @Test
    void twoUsersSendingTheSameRawRequestKeyDoNotBlockOrReplayEachOther() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Vệ sinh khay", "/care-tips/ve-sinh", "Dọn cát hằng ngày.", 1));
        CountingProvider provider = new CountingProvider();
        UUID key = UUID.randomUUID();

        AiChatService.ChatResult first = service(knowledge, provider)
                .chat(USER_ID, null, null, "Cách vệ sinh khay cát?", "vi", key);
        AiChatService.ChatResult second = service(knowledge, provider)
                .chat(UUID.randomUUID(), null, null, "Cách vệ sinh khay cát?", "vi", key);

        assertThat(second.messageId()).isNotEqualTo(first.messageId());
        assertThat(provider.calls).isEqualTo(2);
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT", "USER", "ASSISTANT");
    }

    @Test
    void diagnosisRefusalFollowsTheRequestLanguageButEmergencyCopyStaysVietnamese() {
        FakeKnowledge knowledge = new FakeKnowledge();
        CountingProvider provider = new CountingProvider();

        AiChatService.ChatResult refusal = service(knowledge, provider)
                .chat(USER_ID, null, null, "Can you diagnose my cat?", "en");
        AiChatService.ChatResult emergency = service(knowledge, provider)
                .chat(USER_ID, null, null, "Nước tiểu có máu thì sao?", "en");

        assertThat(refusal.provider()).isEqualTo("refusal-template");
        assertThat(refusal.answer()).startsWith("I can't diagnose");
        assertThat(emergency.answer()).isEqualTo(EXPECTED_EMERGENCY_BODY);
        assertThat(provider.calls).isZero();
    }

    private static AiChatService service(FakeKnowledge knowledge, AiProviderPort provider) {
        return service(knowledge, provider, new FakeCatContextPort());
    }

    private static AiChatService service(FakeKnowledge knowledge, AiProviderPort provider, FakeCatContextPort catContextPort) {
        return service(knowledge, provider, catContextPort, new FakeAiChatRateLimiter());
    }

    private static AiChatService service(FakeKnowledge knowledge, AiProviderPort provider,
            FakeCatContextPort catContextPort, FakeAiChatRateLimiter rateLimiter) {
        return service(knowledge, provider, catContextPort, rateLimiter, userId -> AccountStatus.ACTIVE);
    }

    private static AiChatService service(FakeKnowledge knowledge, AiProviderPort provider,
            FakeCatContextPort catContextPort, FakeAiChatRateLimiter rateLimiter,
            UserAccountPort userAccountPort) {
        return new AiChatService(knowledge, retrieval(knowledge), provider, new AiRuntimeConfig(6, 8), catContextPort,
                rateLimiter, userAccountPort, realMessageResolver(), Clock.fixed(NOW, ZoneOffset.UTC),
                new NoOpTransactionManager());
    }

    private static HybridRetrievalService retrieval(FakeKnowledge knowledge) {
        return new HybridRetrievalService(knowledge, AiEmbeddingPort.NONE, new AiRuntimeConfig(6, 8));
    }

    private static MessageResolver realMessageResolver() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames("classpath:messages/ai");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        return new MessageResolver(messageSource);
    }

    private static final class FakeCatContextPort implements CatContextPort {
        CatContext contextToReturn;
        UUID lastUserId;
        UUID lastCatId;
        boolean called;

        @Override
        public Optional<CatContext> loadContext(UUID userId, UUID catId) {
            called = true;
            lastUserId = userId;
            lastCatId = catId;
            return Optional.ofNullable(contextToReturn);
        }
    }

    @Test
    void listConversationsReturnsUserConversations() {
        FakeKnowledge knowledge = new FakeKnowledge();
        UUID convId = UUID.randomUUID();
        knowledge.conversationSummaries = List.of(
                new AiKnowledgePort.ConversationSummary(convId, USER_ID, NOW, NOW, "Mèo bị tiểu buốt")
        );
        AiChatService service = service(knowledge, messages -> Optional.empty());

        List<AiKnowledgePort.ConversationSummary> result = service.listConversations(USER_ID, 20);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(convId);
        assertThat(result.getFirst().preview()).isEqualTo("Mèo bị tiểu buốt");
    }

    @Test
    void getConversationMessagesReturnsMessagesWhenOwnedByUser() {
        FakeKnowledge knowledge = new FakeKnowledge();
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        knowledge.conversation = new AiKnowledgePort.Conversation(convId, USER_ID);
        knowledge.messageItems = List.of(
                new AiKnowledgePort.MessageItem(msgId, convId, "USER", "Mèo ăn gì tốt?", null, NOW)
        );
        AiChatService service = service(knowledge, messages -> Optional.empty());

        List<AiKnowledgePort.MessageItem> result = service.getConversationMessages(USER_ID, convId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(msgId);
        assertThat(result.getFirst().role()).isEqualTo("USER");
        assertThat(result.getFirst().content()).isEqualTo("Mèo ăn gì tốt?");
    }

    @Test
    void getConversationMessagesThrowsWhenConversationNotFoundOrNotOwned() {
        FakeKnowledge knowledge = new FakeKnowledge();
        UUID convId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        knowledge.conversation = new AiKnowledgePort.Conversation(convId, otherUserId);
        AiChatService service = service(knowledge, messages -> Optional.empty());

        assertThatThrownBy(() -> service.getConversationMessages(USER_ID, convId))
                .isInstanceOf(BusinessRuleException.class)
                .satisfies(ex -> assertThat(((BusinessRuleException) ex).errorCode()).isEqualTo(AiErrorCode.CONVERSATION_NOT_FOUND));
    }

    private static final class FakeAiChatRateLimiter implements AiChatRateLimiter {
        boolean blocked;
        long retryAfterSeconds;
        final List<String> consumedSubjects = new ArrayList<>();

        @Override
        public boolean tryConsume(AiChatRateLimitRule rule, String subject) {
            consumedSubjects.add(subject);
            return !blocked;
        }

        @Override
        public long secondsUntilRefill(AiChatRateLimitRule rule, String subject) {
            return blocked ? retryAfterSeconds : 0;
        }
    }

    private static final class FakeKnowledge implements AiKnowledgePort {
        boolean documents;
        boolean synced;
        boolean onlyVietnamese;
        List<RetrievedChunk> chunks = List.of();
        final List<String> searchedLocales = new ArrayList<>();
        List<String[]> history = List.of();
        Conversation conversation;
        final List<String> savedRoles = new ArrayList<>();
        final List<String> savedContents = new ArrayList<>();
        final java.util.Set<UUID> usedRequestKeys = new java.util.HashSet<>();
        final Map<UUID, AiChatService.ChatResult> repliesByKey = new java.util.HashMap<>();
        UUID savedCitationMessageId;
        final List<Integer> savedCitationRanks = new ArrayList<>();

        @Override
        public CandidateSet searchCandidates(String locale, List<String> terms, float[] queryEmbedding,
                                             String embeddingModel, int perBranch) {
            searchedLocales.add(locale);
            return new CandidateSet(onlyVietnamese && !"vi".equals(locale) ? List.of() : chunks, List.of());
        }

        @Override
        public Conversation createConversation(UUID userId, UUID requestedId) {
            if (requestedId == null) return new Conversation(CONVERSATION_ID, userId);
            if (conversation == null) return new Conversation(requestedId, userId);
            return userId.equals(conversation.userId()) ? conversation : null;
        }

        @Override
        public Optional<UUID> saveUserMessage(UUID conversationId, String content, UUID requestKey) {
            if (requestKey != null && !usedRequestKeys.add(requestKey)) {
                return Optional.empty();
            }
            savedRoles.add("USER");
            savedContents.add(content);
            return Optional.of(UUID.randomUUID());
        }

        @Override
        public void releaseRequestKey(UUID requestKey) {
            usedRequestKeys.remove(requestKey);
        }

        @Override
        public UUID saveAssistantMessage(UUID conversationId, String content, String provider, UUID requestKey) {
            savedRoles.add("ASSISTANT");
            savedContents.add(content);
            UUID id = UUID.randomUUID();
            if (requestKey != null) {
                repliesByKey.put(requestKey, new AiChatService.ChatResult(conversationId, id, content, provider,
                        List.of(), NOW));
            }
            return id;
        }

        @Override
        public Optional<AiChatService.ChatResult> findReply(UUID userId, UUID requestKey) {
            return Optional.ofNullable(repliesByKey.get(requestKey));
        }

        @Override
        public List<String[]> history(UUID conversationId, int limit) {
            return history;
        }

        @Override
        public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
            savedCitationMessageId = messageId;
            chunks.forEach(chunk -> savedCitationRanks.add(chunk.rank()));
        }

        @Override
        public SyncResult syncPublishedCareTips() {
            synced = true;
            documents = true;
            return new SyncResult(1, 1, 0);
        }

        @Override
        public List<PendingChunk> chunksNeedingEmbedding(String embeddingModel, int limit) {
            return List.of();
        }

        @Override
        public void saveEmbeddings(String embeddingModel, Map<UUID, float[]> embeddings) {
        }

        @Override
        public int eraseConversationContent(UUID userId) {
            return 0;
        }

        List<AiKnowledgePort.ConversationSummary> conversationSummaries = List.of();
        List<AiKnowledgePort.MessageItem> messageItems = List.of();

        @Override
        public List<AiKnowledgePort.ConversationSummary> listConversations(UUID userId, int limit) {
            return conversationSummaries;
        }

        @Override
        public List<AiKnowledgePort.MessageItem> listMessages(UUID conversationId) {
            return messageItems;
        }
    }
}
