package com.catcheck.ai.api;

import com.catcheck.ai.api.dto.AiChatRequest;
import com.catcheck.ai.api.dto.AiChatResponse;
import com.catcheck.ai.api.dto.AiConversationResponse;
import com.catcheck.ai.api.dto.AiMessageResponse;
import com.catcheck.ai.application.AiChatService;
import com.catcheck.ai.application.AiKnowledgeIndexer;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI trợ lý", description = "Chatbot RAG dựa trên nội dung chăm sóc đã công bố")
public class AiController {

    private final AiChatService service;
    private final AiKnowledgeIndexer indexer;

    public AiController(AiChatService service, AiKnowledgeIndexer indexer) {
        this.service = service;
        this.indexer = indexer;
    }

    @PostMapping("/chat")
    @Operation(operationId = "chatWithAssistant", summary = "Đặt câu hỏi cho trợ lý CatCheck")
    public AiChatResponse chat(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody AiChatRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) UUID requestKey,
            Locale locale) {
        return AiChatResponse.from(service.chat(principal.userId(), request.conversationId(), request.catId(),
                request.message(), locale.getLanguage(), requestKey));
    }

    @GetMapping("/conversations")
    @Operation(operationId = "listAiConversations", summary = "Lấy danh sách các cuộc trò chuyện của người dùng")
    public List<AiConversationResponse> listConversations(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return service.listConversations(principal.userId(), limit).stream()
                .map(AiConversationResponse::from)
                .toList();
    }

    @GetMapping("/conversations/{id}/messages")
    @Operation(operationId = "getAiConversationMessages", summary = "Lấy toàn bộ tin nhắn của một cuộc trò chuyện")
    public List<AiMessageResponse> getMessages(
            @CurrentUser SecurityPrincipal principal,
            @PathVariable("id") UUID conversationId) {
        return service.getConversationMessages(principal.userId(), conversationId).stream()
                .map(AiMessageResponse::from)
                .toList();
    }

    @PostMapping("/reindex")
    @Operation(operationId = "reindexAiKnowledge", summary = "Đồng bộ lại kho tài liệu RAG từ care tips đã công bố")
    public ReindexResponse reindex(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"));
        return new ReindexResponse(indexer.reindex());
    }

    public record ReindexResponse(int documents) {
    }
}
