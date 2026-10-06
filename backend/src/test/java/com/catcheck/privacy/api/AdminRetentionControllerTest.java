package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.UpdateRetentionPolicyRequest;
import com.catcheck.privacy.application.RetentionService;
import com.catcheck.privacy.domain.RetentionAction;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L56/L57/L58 — ô {@code Q28} ({@code retention_policy}: chỉ {@code DPO} sửa, REQ-RET-06) và
 * ô {@code Q29} (dry-run: {@code ADMIN_SUPER} + {@code DPO}).
 *
 * <p>Bài {@link #exceedingTheI15CeilingSurfacesAs422RetentionLimitExceeded()} là nhánh mã lỗi
 * mà ô L57 của p8 gọi tên đích danh; nó đi qua controller để chứng minh lỗi không bị bộ gác
 * vai trò hay bước merge-patch che mất trước khi tới service.</p>
 */
class AdminRetentionControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID ACTOR = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6670");

    private final RetentionService retentionService = mock(RetentionService.class);
    private final RetentionPolicyPort policyPort = mock(RetentionPolicyPort.class);
    private final AdminRetentionController controller =
            new AdminRetentionController(retentionService, policyPort);

    @Test
    void adminSuperMayReadTheRetentionTable() {
        when(retentionService.listPolicies()).thenReturn(List.of(policy(14)));

        assertThat(controller.list(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"))).hasSize(1);
    }

    @Test
    void supportMayNotReadTheRetentionTable() {
        assertThatThrownBy(() -> controller.list(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void adminSuperMayNotEditRetentionBecauseQ28GivesThatOnlyToDpo() {
        assertThatThrownBy(() -> controller.update(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"),
                "SCAN_IMAGE", request(7)))
                .isInstanceOf(PermissionDeniedException.class);
        verify(retentionService, never()).savePolicy(any(), any());
    }

    @Test
    void exceedingTheI15CeilingSurfacesAs422RetentionLimitExceeded() {
        when(policyPort.findByCode("SCAN_IMAGE")).thenReturn(Optional.of(policy(14)));
        doThrow(new BusinessRuleException(PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED, "SCAN_IMAGE", 14))
                .when(retentionService).savePolicy(any(), any());

        assertThatThrownBy(() -> controller.update(AdminTestPrincipal.of(ACTOR, "DPO"),
                "SCAN_IMAGE", request(30)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED);
    }

    @Test
    void anUnknownPolicyCodeIs404() {
        when(policyPort.findByCode("KHONG_CO")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.update(AdminTestPrincipal.of(ACTOR, "DPO"),
                "KHONG_CO", request(7)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.RETENTION_POLICY_NOT_FOUND);
    }

    @Test
    void adminSuperMayRunTheDryRunBecauseQ29AllowsIt() {
        when(retentionService.dryRun(anyString(), any(), anyString(), any())).thenReturn(
                new RetentionService.DryRunResult("ACCESS_LOG", "access_log", "created_at", 90,
                        NOW.minusSeconds(90L * 86_400), 42L, "HARD_DELETE"));

        var response = controller.dryRun(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPER"), "ACCESS_LOG",
                new MockHttpServletRequest());

        assertThat(response.candidateCount()).isEqualTo(42L);
    }

    @Test
    void supportMayNotRunTheDryRun() {
        assertThatThrownBy(() -> controller.dryRun(AdminTestPrincipal.of(ACTOR, "ADMIN_SUPPORT"),
                "ACCESS_LOG", new MockHttpServletRequest()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(retentionService, never()).dryRun(anyString(), any(), anyString(), any());
    }

    private static UpdateRetentionPolicyRequest request(Integer days) {
        return new UpdateRetentionPolicyRequest(null, null, days, null, null, null, null, null, null);
    }

    private static RetentionPolicy policy(Integer days) {
        return new RetentionPolicy("SCAN_IMAGE", null, "scan_image", days, "uploaded_at",
                RetentionAction.HARD_DELETE, "ScanImageRetentionJob", 20, true, null, ACTOR, NOW);
    }
}
