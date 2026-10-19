package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.RetentionAction;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionDryRunPort;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID DPO_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b66d0");

    private final RetentionPolicyPort policyPort = mock(RetentionPolicyPort.class);
    private final UserAccountPort userAccountPort = mock(UserAccountPort.class);
    private final RetentionDryRunPort dryRunPort = mock(RetentionDryRunPort.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RetentionService service = new RetentionService(
            policyPort, userAccountPort, dryRunPort, auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void scanImageRetentionAbove14DaysIsRejectedWith422RetentionLimitExceeded() {
        givenDpo();

        assertThatThrownBy(() -> service.savePolicy(policy("SCAN_IMAGE_RAW", 15), DPO_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED);
        assertThat(PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED.status().value())
                .as("p8 §8.2.3: ràng buộc ngữ nghĩa ngoài miền giá trị ⇒ 422, không phải 400")
                .isEqualTo(422);
        verify(policyPort, never()).save(any());
    }

    @Test
    void scanImageRetentionAtExactly14DaysIsAccepted() {
        givenDpo();

        service.savePolicy(policy("SCAN_IMAGE_RAW", RetentionService.SCAN_IMAGE_MAX_DAYS), DPO_ID);

        verify(policyPort).save(any());
    }

    @Test
    void accountDeletionGraceAbove7DaysIsRejectedToo() {
        givenDpo();

        assertThatThrownBy(() -> service.savePolicy(policy("ACCOUNT_DELETION_GRACE", 8), DPO_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED);
        verify(policyPort, never()).save(any());
    }

    @Test
    void policyCodesOutsideI15HaveNoCeiling() {
        givenDpo();

        service.savePolicy(policy("CONSENT_RECORD", 365 * 5), DPO_ID);

        assertThat(RetentionService.hardCapFor("CONSENT_RECORD")).isNull();
        assertThat(RetentionService.hardCapFor("SCAN_ANALYSIS_LIFECYCLE"))
                .as("số đo đã khử nhận dạng không phải ảnh — không bị trần 14 ngày")
                .isNull();
        assertThat(RetentionService.hardCapFor("SCAN_IMAGE_RAW")).isEqualTo(14);
        assertThat(RetentionService.hardCapFor("ACCOUNT_DELETION_GRACE")).isEqualTo(7);
        verify(policyPort).save(any());
    }

    @Test
    void onlyDpoMaySaveARetentionPolicy() {
        when(userAccountPort.snapshot(DPO_ID)).thenReturn(snapshot(Set.of("ADMIN_SUPER")));

        assertThatThrownBy(() -> service.savePolicy(policy("SCAN_IMAGE_RAW", 7), DPO_ID))
                .isInstanceOf(PermissionDeniedException.class);
        verify(policyPort, never()).save(any());
    }

    @Test
    void dryRunCountsExpiredRowsWithoutWritingAnything() {
        when(policyPort.findByCode("ACCESS_LOG")).thenReturn(Optional.of(policy("ACCESS_LOG", 90)));
        when(dryRunPort.countExpired(any(), any())).thenReturn(1_234L);

        RetentionService.DryRunResult result = service.dryRun(
                "ACCESS_LOG", DPO_ID, "DPO", RequestEvidence.system());

        assertThat(result.candidateCount()).isEqualTo(1_234L);
        assertThat(result.cutoff()).isEqualTo(NOW.minusSeconds(90L * 86_400));
        verify(policyPort, never()).save(any());

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("RETENTION_DRY_RUN");
        assertThat(audit.getValue().metadata()).containsEntry("candidateCount", 1_234L);
    }

    private void givenDpo() {
        when(userAccountPort.snapshot(DPO_ID)).thenReturn(snapshot(Set.of("DPO")));
    }

    private static UserAccountSnapshot snapshot(Set<String> roles) {
        return new UserAccountSnapshot(DPO_ID, "dpo@catcheck.vn", "ACTIVE",
                null, null, null, null, roles);
    }

    private static RetentionPolicy policy(String code, Integer days) {
        return new RetentionPolicy(code, null, "scan_image", days, "uploaded_at",
                RetentionAction.HARD_DELETE, "ScanImageRetentionJob", 20, true, null, DPO_ID, NOW);
    }

    @Test
    void listPoliciesDelegatesToThePort() {
        when(policyPort.findAll()).thenReturn(List.of(policy("SCAN_IMAGE_RAW", 14)));

        assertThat(service.listPolicies()).hasSize(1);
    }
}
