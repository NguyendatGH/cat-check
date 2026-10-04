package com.catcheck.ai.application;

import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** Kiểm chứng luồng RAG ở tầng application mà không gọi provider ngoài hay Docker. */
class AiChatServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-10-04T07:00:00Z");

    @Test
    void retrievesContextPersistsConversationAndFallsBackWhenProviderUnavailable() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = false;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Theo dõi màu pH", "/care-tips/ph", "Theo dõi màu dưới ánh sáng ổn định.", 1));

        AiChatService.ChatResult result = service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, null, "Làm sao theo dõi màu pH?", "vi");

        assertThat(result.conversationId()).isNotNull();
        assertThat(result.answer()).contains("Theo tài liệu CatCheck");
        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        assertThat(result.citations()).extracting(AiChatService.Citation::title)
                .containsExactly("Theo dõi màu pH");
        assertThat(knowledge.synced).isTrue();
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
        }).chat(USER_ID, requestedConversation, "Câu hỏi mới", "vi");

        assertThat(result.conversationId()).isEqualTo(requestedConversation);
        assertThat(result.answer()).isEqualTo("Câu trả lời có căn cứ [1]");
        assertThat(result.provider()).isEqualTo("openai-compatible");
        assertThat(sentMessages).extracting(message -> message.get("role"))
                .containsExactly("system", "user", "assistant", "user");
        assertThat(sentMessages.getLast().get("content")).contains("Câu hỏi mới");
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    @Test
    void redactsContactPiiFromCurrentPromptAndHistoryButPersistsOriginalQuestion() {
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
        }).chat(USER_ID, null, question, "vi");

        String providerText = sentMessages.stream().map(message -> message.get("content"))
                .reduce("", (left, right) -> left + "\n" + right);
        assertThat(providerText).doesNotContain("meo@example.test", "0912 345 678", "an@example.test", "0987654321");
        assertThat(providerText).contains("[đã ẩn email]", "[đã ẩn số điện thoại]");
        assertThat(knowledge.savedContents).contains(question);
    }

    @Test
    void fallsBackToVietnameseKnowledgeWhenRequestedLocaleHasNoPublishedDocuments() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        knowledge.onlyVietnamese = true;
        knowledge.chunks = List.of(new AiKnowledgePort.RetrievedChunk(
                UUID.randomUUID(), "Cách chụp ảnh khay cát", "/care-tips/photo", "Dọn khay trước khi chụp.", 1));

        AiChatService.ChatResult result = service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, null, "How do I take a litter photo?", "en");

        assertThat(knowledge.searchedLocales).containsExactly("en", "vi");
        assertThat(result.citations()).extracting(AiChatService.Citation::title).containsExactly("Cách chụp ảnh khay cát");
    }

    @Test
    void rejectsConversationOwnedByAnotherUser() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.conversation = new AiKnowledgePort.Conversation(CONVERSATION_ID, UUID.randomUUID());

        assertThatThrownBy(() -> service(knowledge, messages -> Optional.empty())
                .chat(USER_ID, CONVERSATION_ID, "Xin chào", "vi"))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void refusesWithoutCallingProviderWhenRetrievalHasNoEvidence() {
        FakeKnowledge knowledge = new FakeKnowledge();
        knowledge.documents = true;
        AiProviderPort provider = mock(AiProviderPort.class);

        AiChatService.ChatResult result = service(knowledge, provider)
                .chat(USER_ID, null, "Mèo nhà tôi bị bệnh gì?", "vi");

        assertThat(result.answer()).contains("chưa tìm thấy tài liệu");
        assertThat(result.citations()).isEmpty();
        assertThat(result.provider()).isEqualTo("retrieval-fallback");
        verifyNoInteractions(provider);
        assertThat(knowledge.savedRoles).containsExactly("USER", "ASSISTANT");
    }

    private static AiChatService service(FakeKnowledge knowledge, AiProviderPort provider) {
        return new AiChatService(knowledge, provider, new AiRuntimeConfig(6, 8),
                Clock.fixed(NOW, ZoneOffset.UTC));
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
        UUID savedCitationMessageId;

        @Override
        public boolean hasDocuments() {
            return documents;
        }

        @Override
        public List<RetrievedChunk> search(String locale, String query, int limit) {
            searchedLocales.add(locale);
            return onlyVietnamese && !"vi".equals(locale) ? List.of() : chunks;
        }

        @Override
        public Conversation createConversation(UUID userId, UUID requestedId) {
            if (requestedId == null) return new Conversation(CONVERSATION_ID, userId);
            if (conversation == null) return new Conversation(requestedId, userId);
            return userId.equals(conversation.userId()) ? conversation : null;
        }

        @Override
        public UUID saveMessage(UUID conversationId, String role, String content, String provider) {
            savedRoles.add(role);
            savedContents.add(content);
            return UUID.randomUUID();
        }

        @Override
        public List<String[]> history(UUID conversationId, int limit) {
            return history;
        }

        @Override
        public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) {
            savedCitationMessageId = messageId;
        }

        @Override
        public int syncPublishedCareTips() {
            synced = true;
            documents = true;
            return 1;
        }
    }
}
