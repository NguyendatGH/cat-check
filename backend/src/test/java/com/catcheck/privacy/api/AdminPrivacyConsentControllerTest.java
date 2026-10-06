package com.catcheck.privacy.api;

import com.catcheck.privacy.domain.ConsentMethod;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L55 — p8 ghi {@code R:DPO} và p14 ô {@code Q25} để ❌ cho cả {@code ADMIN_SUPER}.
 *
 * <p>Đây là ô mà p14 ghi rõ bản trước "không có dòng nào" và được bổ sung theo p11 §11.5.4,
 * nên nhánh "ADMIN_SUPER bị từ chối" là khẳng định đáng giữ nhất ở đây: nó dễ bị nới ra một
 * cách thiện chí ("Super thì xem gì chẳng được") và hậu quả là bảng bằng chứng pháp lý thành
 * dữ liệu hỗ trợ khách hàng.</p>
 */
class AdminPrivacyConsentControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID ACTOR = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6640");
    private static final UUID SUBJECT = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6641");

    private final ConsentRecordPort consentRecordPort = mock(ConsentRecordPort.class);
    private final AdminPrivacyConsentController controller =
            new AdminPrivacyConsentController(consentRecordPort, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void adminSuperCannotReadConsentEvidence() {
        assertThatThrownBy(() -> controller.list(
                AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"), SUBJECT, null, null))
                .isInstanceOf(PermissionDeniedException.class);
        verify(consentRecordPort, never()).findHistoryByUser(any(), any(), any(), anyInt());
    }

    @Test
    void supportCannotReadConsentEvidenceEither() {
        assertThatThrownBy(() -> controller.list(
                AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT"), SUBJECT, null, null))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void dpoReadsTheFullEvidenceRowIncludingConsentTextHash() {
        when(consentRecordPort.findHistoryByUser(eq(SUBJECT), eq(NOW), eq(null), anyInt()))
                .thenReturn(List.of(record()));

        var page = controller.list(AdminTestPrincipal.of(ACTOR, "DPO"), SUBJECT, null, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().consentTextHash()).isEqualTo("text-hash");
        assertThat(page.items().getFirst().purposeCode()).isEqualTo("ALGO_IMPROVEMENT");
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void aFullPageHandsBackACursorThatPointsAtTheLastRow() {
        ConsentRecord last = record();
        when(consentRecordPort.findHistoryByUser(eq(SUBJECT), eq(NOW), eq(null), eq(3)))
                .thenReturn(List.of(record(), last, record()));

        var page = controller.list(AdminTestPrincipal.of(ACTOR, "DPO"), SUBJECT, null, 2);

        assertThat(page.items()).hasSize(2);
        assertThat(page.hasMore()).isTrue();
        // Cursor phải mang CẢ id của dòng cuối: 4 dòng consent ghi cùng transaction có
        // occurred_at giống hệt nhau, cursor chỉ có thời điểm sẽ bỏ sót phần còn lại của nhóm.
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(page.nextCursor()),
                java.nio.charset.StandardCharsets.UTF_8);
        assertThat(decoded).isEqualTo(last.occurredAt() + "|" + last.id());
    }

    @Test
    void aCursorFromThePreviousPageIsPassedToThePortAsAPair() {
        ConsentRecord first = record();
        when(consentRecordPort.findHistoryByUser(eq(SUBJECT), eq(NOW), eq(null), eq(2)))
                .thenReturn(List.of(first, record()));

        var page = controller.list(AdminTestPrincipal.of(ACTOR, "DPO"), SUBJECT, null, 1);
        controller.list(AdminTestPrincipal.of(ACTOR, "DPO"), SUBJECT, page.nextCursor(), 1);

        verify(consentRecordPort).findHistoryByUser(
                eq(SUBJECT), eq(first.occurredAt()), eq(first.id()), eq(2));
    }

    @Test
    void aMalformedCursorIs400NotAStackTrace() {
        assertThatThrownBy(() -> controller.list(
                AdminTestPrincipal.of(ACTOR, "DPO"), SUBJECT, "khong-phai-base64-hop-le!!", null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.PAGINATION_CURSOR_INVALID);
    }

    private static ConsentRecord record() {
        return new ConsentRecord(UUID.randomUUID(), SUBJECT, "ALGO_IMPROVEMENT",
                ConsentStatus.GRANTED, UUID.randomUUID(), "policy-hash", "text-hash",
                ConsentMethod.WEB_CHECKBOX, "register", "vi", null, NOW.minusSeconds(60),
                "203.0.113.7", "Mozilla/5.0", "req-1", Map.of("screen", "register"), NOW.minusSeconds(60));
    }
}
