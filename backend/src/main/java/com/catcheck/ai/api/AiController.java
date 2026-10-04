package com.catcheck.ai.api;

import com.catcheck.ai.api.dto.AiChatRequest;
import com.catcheck.ai.api.dto.AiChatResponse;
import com.catcheck.ai.application.AiChatService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/ai")
@Tag(name = "AI trợ lý", description = "Chatbot RAG dựa trên nội dung chăm sóc đã công bố")
public class AiController {

    private final AiChatService service;

    public AiController(AiChatService service) {
        this.service = service;
    }

    @PostMapping("/chat")
    @Operation(operationId = "chatWithAssistant", summary = "Đặt câu hỏi cho trợ lý CatCheck")
    public AiChatResponse chat(
            @CurrentUser SecurityPrincipal principal,
            @Valid @RequestBody AiChatRequest request,
            Locale locale) {
        return AiChatResponse.from(service.chat(principal.userId(), request.conversationId(), request.message(), locale.getLanguage()));
    }

    @PostMapping("/reindex")
    @Operation(operationId = "reindexAiKnowledge", summary = "Đồng bộ lại kho tài liệu RAG từ care tips đã công bố")
    public ReindexResponse reindex(@CurrentUser SecurityPrincipal principal) {
        AdminGuard.requireAnyRole(principal, Set.of("ADMIN_SUPER", "ADMIN_SUPPORT", "DPO"));
        return new ReindexResponse(service.reindex());
    }

    public record ReindexResponse(int documents) {
    }
}
