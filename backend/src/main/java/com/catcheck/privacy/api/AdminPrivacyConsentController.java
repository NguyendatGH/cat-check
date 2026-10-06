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

/**
 * L55 — {@code GET /api/v1/admin/privacy/consents?userId=}: bằng chứng
 * {@code consent_record} của một chủ thể dữ liệu.
 *
 * <p><b>Chỉ {@code DPO}.</b> p8 §8.4.12 ô L55 ghi {@code R:DPO} và p14 §14.2.2 ô
 * {@code Q25} để ❌ cho cả {@code ADMIN_SUPER}, {@code ADMIN_CATALOG},
 * {@code ADMIN_SUPPORT} — đây là bảng bằng chứng pháp lý (Điều 6.2 NĐ356), không phải dữ
 * liệu hỗ trợ khách hàng. Q25 là một trong bốn ô mà p14 ghi rõ đã được mở/siết theo p11
 * §11.5.4, nên không nới thêm vai trò nào ở đây.</p>
 *
 * <p><b>Tại sao là controller riêng mà không nối vào {@code AdminPrivacyController}:</b>
 * controller đó {@code @RequestMapping("/api/v1/admin/privacy/requests")}; L55 là
 * {@code /admin/privacy/consents} — path khác, vai trò khác (một nơi cho cả Support đọc,
 * một nơi chỉ DPO), nên gộp vào sẽ để hai mức quyền khác nhau trong cùng một lớp.</p>
 *
 * <p>Phân trang <b>cursor</b> theo đúng cột "Trang = {@code C}" của ô L55: keyset theo CẶP
 * {@code (occurred_at, id)}. Bảng này append-only (bất biến I16) nên keyset không bao giờ nhảy
 * dòng giữa hai trang — khác offset, vốn lệch ngay khi có dòng mới chèn vào đầu. Cặp chứ không
 * chỉ {@code occurred_at}: một lần ghi consent sinh nhiều dòng cùng mốc thời gian, xem javadoc
 * {@code ConsentRecordPort#findHistoryByUser}.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/privacy/consents")
@Tag(name = "Quản trị consent", description = "Bằng chứng đồng ý của một chủ thể dữ liệu — chỉ DPO")
public class AdminPrivacyConsentController {

    /** p8 L55 {@code R:DPO} + p14 Q25: vai trò duy nhất. */
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

        // +1 dòng để biết còn trang sau hay không, không cần một câu COUNT thứ hai.
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
            // Trang đầu: "trước thời điểm hiện tại" — qua Clock đã inject (R13), không Instant.now().
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

    /**
     * Cursor mang CẢ {@code occurredAt} và {@code id}: nhiều mục đích được ghi trong cùng
     * transaction nên trùng {@code occurred_at}, và cursor chỉ có thời điểm sẽ nhảy qua phần
     * còn lại của nhóm — xem javadoc {@code ConsentRecordPort#findHistoryByUser}.
     */
    private record Cursor(Instant before, UUID beforeId) {
    }

    private static String encodeCursor(ConsentRecord last) {
        String raw = last.occurredAt().toString() + "|" + last.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
