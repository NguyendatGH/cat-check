package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AdminConsentRecordView;
import com.catcheck.privacy.api.dto.PageView;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.AdminGuard;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/privacy/consents")
@Tag(name = "Quản trị consent", description = "Bằng chứng đồng ý của một chủ thể dữ liệu — chỉ DPO")
public class AdminPrivacyConsentController {

    private static final Set<String> READ_ROLES = Set.of("DPO");

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final DateTimeFormatter CURSOR_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final ConsentRecordPort consentRecordPort;
    private final Clock clock;

    public AdminPrivacyConsentController(ConsentRecordPort consentRecordPort, Clock clock) {
        this.consentRecordPort = consentRecordPort;
        this.clock = clock;
    }

    @Operation(operationId = "listAdminPrivacyConsents",
            summary = "L55 — bằng chứng consent của một user (chỉ DPO)")
    @GetMapping
    public PageView<AdminConsentRecordView> list(
            @CurrentUser SecurityPrincipal principal,
            @RequestParam UUID userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        AdminGuard.requireAnyRole(principal, READ_ROLES);
        int effectiveLimit = limit == null ? DEFAULT_LIMIT : Math.clamp(limit, 1, MAX_LIMIT);
        Cursor position = decodeCursor(cursor);

        List<ConsentRecord> rows = consentRecordPort.findHistoryByUser(
                userId, position.before(), position.beforeId(), effectiveLimit + 1);
        boolean hasMore = rows.size() > effectiveLimit;
        List<ConsentRecord> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        String nextCursor = hasMore && !page.isEmpty()
                ? encodeCursor(page.get(page.size() - 1))
                : null;
        return PageView.of(page.stream().map(AdminConsentRecordView::from).toList(),
                effectiveLimit, nextCursor, hasMore);
    }

    private Cursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new Cursor(clock.instant(), null);
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new Cursor(Instant.from(CURSOR_INSTANT.parse(parts[0])),
                    parts.length > 1 && !parts[1].isBlank() ? UUID.fromString(parts[1]) : null);
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.PAGINATION_CURSOR_INVALID);
        }
    }

    private record Cursor(Instant before, UUID beforeId) {
    }

    private static String encodeCursor(ConsentRecord last) {
        String raw = last.occurredAt().toString() + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
