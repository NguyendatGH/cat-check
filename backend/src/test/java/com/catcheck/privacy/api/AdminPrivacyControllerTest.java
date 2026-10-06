package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.AdminReasonRequest;
import com.catcheck.privacy.application.AdminDsarService;
import com.catcheck.privacy.application.AdminErasureApprovalService;
import com.catcheck.privacy.application.export.DataExportJobService;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.security.AdminApiErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Nhánh phân quyền + {@code Rsn} của L52/L53 ở tầng API (p8 §8.4.12, p14 §14.2.2 ô Q10/Q9).
 *
 * <p>Bài test gọi thẳng controller thay vì qua MockMvc vì thứ đang kiểm là bộ gác
 * {@code AdminGuard} mà <b>mỗi</b> endpoint phải tự gọi — p14 §14.5.1 mục 2: "UI ẩn nút với
 * vai trò không có quyền, nhưng server vẫn phải kiểm độc lập". Lớp chặn chung
 * ({@code mfaLevel = TOTP}) đã có bài riêng ở {@code AdminMfaGateFilterTest}.</p>
 */
class AdminPrivacyControllerTest {

    private static final UUID ACTOR = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6630");
    private static final UUID REQUEST_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6631");
    private static final String GOOD_REASON = "Xu ly yeu cau DSAR so 123";

    private final DataExportJobService exportService = mock(DataExportJobService.class);
    private final AdminErasureApprovalService erasureService = mock(AdminErasureApprovalService.class);
    private final AdminPrivacyController controller = new AdminPrivacyController(
            mock(DsarRequestPort.class), mock(AdminDsarService.class), exportService, erasureService);

    @Test
    void supportCannotTriggerAnExportOnBehalfOfASubject() {
        assertThatThrownBy(() -> controller.export(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT"),
                REQUEST_ID, new AdminReasonRequest(GOOD_REASON), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(exportService, never()).enqueueOnBehalf(any(), any(), anyString(), anyString(), any());
    }

    @Test
    void dpoMayTriggerAnExportAndTheReasonReachesTheService() {
        when(exportService.enqueueOnBehalf(eq(REQUEST_ID), eq(ACTOR), eq("DPO"), eq(GOOD_REASON), any()))
                .thenReturn(new DataExportJobService.AdminDispatch("DSAR-2026-000001", "QUEUED", true));

        var response = controller.export(AdminTestPrincipal.of(ACTOR, "DPO"), REQUEST_ID,
                new AdminReasonRequest(GOOD_REASON), new MockHttpServletRequest());

        assertThat(response.publicRef()).isEqualTo("DSAR-2026-000001");
        assertThat(response.dispatched()).isTrue();
    }

    @Test
    void adminSuperCannotApproveAnErasureBecauseQ9GivesThatOnlyToDpo() {
        assertThatThrownBy(() -> controller.approveErasure(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                REQUEST_ID, new AdminReasonRequest(GOOD_REASON), new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(erasureService, never()).approve(any(), any(), anyString(), any());
    }

    @Test
    void aReasonShorterThanTenCharactersIsRejectedBeforeAnythingIsErased() {
        assertThatThrownBy(() -> controller.approveErasure(AdminTestPrincipal.of(ACTOR, "DPO"),
                REQUEST_ID, new AdminReasonRequest("ngan"), new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.REASON_REQUIRED);
        verify(erasureService, never()).approve(any(), any(), anyString(), any());
    }

    @Test
    void dpoApprovalDelegatesWithTheTrimmedReason() {
        when(erasureService.approve(eq(ACTOR), eq(REQUEST_ID), eq(GOOD_REASON), any()))
                .thenReturn(new AdminErasureApprovalService.Result(
                        "DSAR-2026-000002", REQUEST_ID, 5, Instant.parse("2026-10-06T03:00:00Z")));

        var response = controller.approveErasure(AdminTestPrincipal.of(ACTOR, "DPO"), REQUEST_ID,
                new AdminReasonRequest("   " + GOOD_REASON + "   "), new MockHttpServletRequest());

        assertThat(response.participantsInvoked()).isEqualTo(5);
        verify(erasureService).approve(eq(ACTOR), eq(REQUEST_ID), eq(GOOD_REASON), any());
    }
}
