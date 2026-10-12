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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiChatService {

    private static final Logger log = LoggerFactory.getLogger(AiChatService.class);

    private static final String SYSTEM_PROMPT = "Bạn là trợ lý CatCheck. Chỉ trả lời dựa trên CONTEXT "
            + "được cung cấp, không chẩn đoán bệnh và không bịa nguồn. Nếu dữ liệu không đủ, nói rõ "
            + "không đủ dữ liệu và khuyên người dùng liên hệ bác sĩ thú y. Trích dẫn bằng [1], [2] "
            + "theo danh sách nguồn. Không yêu cầu hoặc lặp lại email/số điện thoại của người dùng.";

    private static final Locale EMERGENCY_LOCALE = Locale.forLanguageTag("vi");

    private static final Pattern CITATION_MARKER = Pattern.compile("\\[(\\d+)]");

    private final AiKnowledgePort repository;
    private final AiProviderPort provider;
    private final AiRuntimeConfig config;
    private final CatContextPort catContextPort;
    private final AiChatRateLimiter rateLimiter;
    private final UserAccountPort userAccountPort;
    private final MessageResolver messageResolver;
    private final Clock clock;

    public AiChatService(
            AiKnowledgePort repository,
            AiProviderPort provider,
            AiRuntimeConfig config,
            CatContextPort catContextPort,
            AiChatRateLimiter rateLimiter,
            UserAccountPort userAccountPort,
            MessageResolver messageResolver,
            Clock clock) {
        this.repository = repository;
        this.provider = provider;
        this.config = config;
        this.catContextPort = catContextPort;
        this.rateLimiter = rateLimiter;
        this.userAccountPort = userAccountPort;
        this.messageResolver = messageResolver;
        this.clock = clock;
    }

    public record Citation(String title, String sourceUrl, int rank) {
    }

    public record ChatResult(UUID conversationId, UUID messageId, String answer, String provider,
                             List<Citation> citations, Instant createdAt) {
    }

    @Transactional
    public ChatResult chat(UUID userId, UUID conversationId, UUID catId, String message, String locale) {
        requireWriteAllowed(userId);
        requireRateLimit(userId);

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

        Optional<CatContextPort.CatContext> catContext =
                catId == null ? Optional.empty() : catContextPort.loadContext(userId, catId);

        if (catContext.isPresent() && CatContextPresenter.hasUrgentFlag(catContext.get())
                || AiEmergencySigns.mentionsEmergencySign(normalized)) {
            return emergencyResult(conversation, normalized, catContext.orElse(null));
        }

        String requestedLocale = locale == null || locale.isBlank() ? "vi" : locale;
        int contextLimit = Math.clamp(config.maxContextChunks(), 1, 12);

        String augmentation = catContext.map(CatContextPresenter::augmentationKeywords).orElse("");
        String searchQuery = augmentation.isBlank() ? normalized : normalized + " " + augmentation;

        List<AiKnowledgePort.RetrievedChunk> chunks = repository.search(requestedLocale, searchQuery, contextLimit);
        if (chunks.isEmpty() && !"vi".equalsIgnoreCase(requestedLocale)) {
            chunks = repository.search("vi", searchQuery, contextLimit);
        }
        chunks = numberedByPosition(chunks);

        String catContextBlock = catContext.map(CatContextPresenter::renderContext).filter(block -> !block.isBlank())
                .orElse(null);

        List<Map<String, String>> prompt = new ArrayList<>();
        prompt.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        for (String[] item : repository.history(conversation.id(), Math.clamp(config.maxHistoryMessages(), 0, 12))) {
            prompt.add(Map.of("role", item[0].equals("USER") ? "user" : "assistant",
                    "content", AiPromptRedactor.redact(item[1])));
        }
        prompt.add(Map.of("role", "user",
                "content", buildPrompt(AiPromptRedactor.redact(normalized), chunks, catContextBlock)));

        repository.saveMessage(conversation.id(), "USER", AiPromptRedactor.redact(normalized), "user");
        var generated = chunks.isEmpty() ? java.util.Optional.<String>empty() : provider.complete(prompt);
        String answer;
        String providerName;
        if (generated.isPresent()) {
            answer = generated.get();
            providerName = "openai-compatible";
            warnIfCitationsLookInvalid(answer, chunks.size());
        } else {
            answer = fallbackAnswer(chunks);
            providerName = "retrieval-fallback";
        }
        UUID messageId = repository.saveMessage(conversation.id(), "ASSISTANT", answer, providerName);
        repository.saveCitations(messageId, chunks);
        List<Citation> citations = chunks.stream().map(chunk -> new Citation(chunk.title(), chunk.sourceUrl(), chunk.rank())).toList();
        return new ChatResult(conversation.id(), messageId, answer, providerName, citations, clock.instant());
    }

    public int reindex() {
        return repository.syncPublishedCareTips();
    }

    private ChatResult emergencyResult(AiKnowledgePort.Conversation conversation, String normalizedMessage,
            CatContextPort.CatContext context) {
        repository.saveMessage(conversation.id(), "USER", AiPromptRedactor.redact(normalizedMessage), "user");
        String body = messageResolver.resolve("ai.emergency.body", new Object[0], EMERGENCY_LOCALE);
        String answer = body;
        if (context != null) {
            String catName = context.name() == null || context.name().isBlank() ? "Bé mèo" : context.name();
            answer = messageResolver.resolve("ai.emergency.lead", new Object[]{catName}, EMERGENCY_LOCALE)
                    + "\n\n" + body
                    + "\n\n" + messageResolver.resolve("ai.emergency.viewDetail", new Object[]{context.catId()}, EMERGENCY_LOCALE);
        }
        UUID messageId = repository.saveMessage(conversation.id(), "ASSISTANT", answer, "emergency-template");
        repository.saveCitations(messageId, List.of());
        return new ChatResult(conversation.id(), messageId, answer, "emergency-template", List.of(), clock.instant());
    }

    private static List<AiKnowledgePort.RetrievedChunk> numberedByPosition(List<AiKnowledgePort.RetrievedChunk> chunks) {
        List<AiKnowledgePort.RetrievedChunk> numbered = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            AiKnowledgePort.RetrievedChunk chunk = chunks.get(i);
            numbered.add(new AiKnowledgePort.RetrievedChunk(chunk.documentId(), chunk.title(), chunk.sourceUrl(),
                    chunk.content(), i + 1));
        }
        return numbered;
    }

    private String buildPrompt(String message, List<AiKnowledgePort.RetrievedChunk> chunks, String catContextBlock) {
        StringBuilder context = new StringBuilder("USER QUESTION:\n").append(message).append("\n\n");
        if (catContextBlock != null) {
            context.append(catContextBlock).append("\n\n");
        }
        context.append("CONTEXT:\n");
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
        StringBuilder answer = new StringBuilder();
        if (chunks.isEmpty()) {
            return answer.append("Mình chưa tìm thấy tài liệu chăm sóc phù hợp cho câu hỏi này. Bạn nên trao đổi với bác sĩ thú y nếu mèo có dấu hiệu bất thường.").toString();
        }
        answer.append("Theo tài liệu CatCheck, bạn có thể tham khảo:\n");
        for (int i = 0; i < Math.min(chunks.size(), 3); i++) {
            answer.append("- ").append(chunks.get(i).content().replaceAll("\\s+", " ").strip()).append(" [")
                    .append(i + 1).append("]\n");
        }
        return answer.append("Đây là thông tin tham khảo, không thay thế chẩn đoán của bác sĩ thú y.").toString();
    }

    private void requireWriteAllowed(UUID userId) {
        AccountStatus status = userAccountPort.statusOf(userId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(AiErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    private void requireRateLimit(UUID userId) {
        AiChatRateLimiter.RetryAfter retryAfter =
                rateLimiter.rejectIfLimited(AiChatRateLimitRule.AI_CHAT_BY_USER, userId.toString());
        if (retryAfter != null) {
            throw new RateLimitedException(AiErrorCode.AI_CHAT_RATE_LIMITED,
                    retryAfter.headerSeconds(), AiChatRateLimitRule.AI_CHAT_BY_USER.name());
        }
    }

    private void warnIfCitationsLookInvalid(String answer, int availableCitations) {
        Matcher matcher = CITATION_MARKER.matcher(answer);
        int invalid = 0;
        int total = 0;
        while (matcher.find()) {
            total++;
            int n = Integer.parseInt(matcher.group(1));
            if (n < 1 || n > availableCitations) {
                invalid++;
            }
        }
        if (invalid > 0) {
            log.warn("AI answer references {} out-of-range citation marker(s) out of {} total "
                    + "(availableCitations={}) — preliminary safety net, not yet enforced (no real "
                    + "provider in prod to validate behavior against).", invalid, total, availableCitations);
        }
    }
}
