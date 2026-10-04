package com.catcheck.ai.application;

import com.catcheck.ai.api.AiErrorCode;
import com.catcheck.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AiChatService {

    private static final String SYSTEM_PROMPT = "Bạn là trợ lý CatCheck. Chỉ trả lời dựa trên CONTEXT "
            + "được cung cấp, không chẩn đoán bệnh và không bịa nguồn. Nếu dữ liệu không đủ, nói rõ "
            + "không đủ dữ liệu và khuyên người dùng liên hệ bác sĩ thú y. Trích dẫn bằng [1], [2] "
            + "theo danh sách nguồn. Không yêu cầu hoặc lặp lại email/số điện thoại của người dùng.";

    private final AiKnowledgePort repository;
    private final AiProviderPort provider;
    private final AiRuntimeConfig config;
    private final Clock clock;

    public AiChatService(AiKnowledgePort repository, AiProviderPort provider, AiRuntimeConfig config, Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.config = config;
        this.clock = clock;
    }

    public record Citation(String title, String sourceUrl, int rank) {
    }

    public record ChatResult(UUID conversationId, UUID messageId, String answer, String provider,
                             List<Citation> citations, Instant createdAt) {
    }

    @Transactional
    public ChatResult chat(UUID userId, UUID conversationId, String message, String locale) {
        String normalized = message == null ? "" : message.strip();
        if (normalized.isEmpty() || normalized.length() > 4000) {
            throw new BusinessRuleException(AiErrorCode.AI_MESSAGE_INVALID);
        }
        if (!repository.hasDocuments()) {
            repository.syncPublishedCareTips();
        }
        AiKnowledgePort.Conversation conversation = repository.createConversation(userId, conversationId);
        if (conversation == null) {
            throw new BusinessRuleException(AiErrorCode.CONVERSATION_NOT_FOUND);
        }

        String requestedLocale = locale == null || locale.isBlank() ? "vi" : locale;
        int contextLimit = Math.clamp(config.maxContextChunks(), 1, 12);
        List<AiKnowledgePort.RetrievedChunk> chunks = repository.search(requestedLocale, normalized, contextLimit);
        // The UI can render Vietnamese through i18n fallback while the browser preference
        // still reports `en`; use the published Vietnamese corpus rather than silently
        // returning no evidence for an unsupported knowledge locale.
        if (chunks.isEmpty() && !"vi".equalsIgnoreCase(requestedLocale)) {
            chunks = repository.search("vi", normalized, contextLimit);
        }
        List<Map<String, String>> prompt = new ArrayList<>();
        prompt.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (String[] item : repository.history(conversation.id(), Math.clamp(config.maxHistoryMessages(), 0, 12))) {
            prompt.add(Map.of("role", item[0].equals("USER") ? "user" : "assistant",
                    "content", AiPromptRedactor.redact(item[1])));
        }
        prompt.add(Map.of("role", "user", "content", buildPrompt(AiPromptRedactor.redact(normalized), chunks)));

        repository.saveMessage(conversation.id(), "USER", normalized, "user");
        // Không gửi câu hỏi ra provider khi retrieval không có bằng chứng. Prompting một LLM
        // chỉ với câu hỏi sẽ biến RAG thành chatbot tự do và có thể tạo lời khuyên y tế bịa.
        var generated = chunks.isEmpty() ? java.util.Optional.<String>empty() : provider.complete(prompt);
        String answer = generated.isPresent() ? generated.get() : fallbackAnswer(chunks);
        String providerName = generated.isPresent() ? "openai-compatible" : "retrieval-fallback";
        UUID messageId = repository.saveMessage(conversation.id(), "ASSISTANT", answer, providerName);
        repository.saveCitations(messageId, chunks);
        List<Citation> citations = chunks.stream().map(chunk -> new Citation(chunk.title(), chunk.sourceUrl(), chunk.rank())).toList();
        return new ChatResult(conversation.id(), messageId, answer, providerName, citations, clock.instant());
    }

    public int reindex() {
        return repository.syncPublishedCareTips();
    }

    private String buildPrompt(String message, List<AiKnowledgePort.RetrievedChunk> chunks) {
        StringBuilder context = new StringBuilder("USER QUESTION:\n").append(message).append("\n\nCONTEXT:\n");
        if (chunks.isEmpty()) {
            return context.append("(Không tìm thấy tài liệu phù hợp.)").toString();
        }
        for (int i = 0; i < chunks.size(); i++) {
            context.append('[').append(i + 1).append("] ").append(chunks.get(i).title()).append("\n")
                    .append(chunks.get(i).content()).append("\n\n");
        }
        return context.toString();
    }

    private String fallbackAnswer(List<AiKnowledgePort.RetrievedChunk> chunks) {
        if (chunks.isEmpty()) {
            return "Mình chưa tìm thấy tài liệu chăm sóc phù hợp cho câu hỏi này. Bạn nên trao đổi với bác sĩ thú y nếu mèo có dấu hiệu bất thường.";
        }
        StringBuilder answer = new StringBuilder("Theo tài liệu CatCheck, bạn có thể tham khảo:\n");
        for (int i = 0; i < Math.min(chunks.size(), 3); i++) {
            answer.append("- ").append(chunks.get(i).content().replaceAll("\\s+", " ").strip()).append(" [")
                    .append(i + 1).append("]\n");
        }
        return answer.append("Đây là thông tin tham khảo, không thay thế chẩn đoán của bác sĩ thú y.").toString();
    }
}
