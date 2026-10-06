package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/**
 * L53 — quy tắc hai người của p15 REQ-RBAC-03 / p14 ô Q9.
 *
 * <p>Mỗi bài dưới đây là một nhánh mà nếu thiếu thì "hai người" chỉ còn là chữ trong tài
 * liệu: vai trò sai, tự duyệt, không biết ai đề xuất, chưa xác minh danh tính. Bài quan
 * trọng nhất là {@link #theProposerCannotApproveTheirOwnErasureRequest()}: đó là trường hợp
 * duy nhất mà bộ gác vai trò <i>không</i> tự chặn, vì Q8 cho chính {@code DPO} đề xuất.</p>
 */
class AdminErasureApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID PROPOSER = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6601");
    private static final UUID APPROVER = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6602");
    private static final UUID SUBJECT = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6603");
    private static final UUID REQUEST_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6604");

    private final DsarRequestPort dsarPort = mock(DsarRequestPort.class);
    private final ErasureCoordinator erasureCoordinator = mock(ErasureCoordinator.class);
    private final UserAccountPort userAccountPort = mock(UserAccountPort.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AdminErasureApprovalService service = new AdminErasureApprovalService(
            dsarPort, erasureCoordinator, userAccountPort, auditLogService,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void aDifferentDpoApprovesAndTheErasureActuallyRuns() {
        givenRole(APPROVER, "DPO");
        when(erasureCoordinator.participantCount()).thenReturn(5);
        givenRequest(eraseRequest(DsarChannel.EMAIL, PROPOSER, NOW, DsarStatus.IN_PROGRESS));

        AdminErasureApprovalService.Result result =
                service.approve(APPROVER, REQUEST_ID, "Chu the yeu cau qua buu dien", RequestEvidence.system());

        verify(erasureCoordinator).erase(SUBJECT);
        assertThat(result.subjectId()).isEqualTo(SUBJECT);
        assertThat(result.completedAt()).isEqualTo(NOW);
        assertThat(result.participantsInvoked()).isEqualTo(5);

        ArgumentCaptor<DsarRequest> saved = ArgumentCaptor.forClass(DsarRequest.class);
        verify(dsarPort).update(saved.capture());
        assertThat(saved.getValue().status()).isEqualTo(DsarStatus.COMPLETED);
        assertThat(saved.getValue().completedAt()).isEqualTo(NOW);

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("DSAR_ERASURE_APPROVED");
        assertThat(audit.getValue().metadata())
                .as("p15 REQ-AUD-03: ai đề xuất phải nằm trong bằng chứng, không chỉ ai duyệt")
                .containsEntry("proposedBy", PROPOSER);
    }

    @Test
    void theProposerCannotApproveTheirOwnErasureRequest() {
        givenRole(PROPOSER, "DPO");
        givenRequest(eraseRequest(DsarChannel.EMAIL, PROPOSER, NOW, DsarStatus.IN_PROGRESS));

        assertThatThrownBy(() -> service.approve(PROPOSER, REQUEST_ID, "Tu duyet cho chinh minh", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.DSAR_SELF_APPROVAL_FORBIDDEN);
        verify(erasureCoordinator, never()).erase(any());
        verify(dsarPort, never()).update(any());
    }

    @Test
    void adminSuperCannotApproveEvenWhenSomebodyElseProposed() {
        givenRole(APPROVER, "ADMIN_SUPER");
        givenRequest(eraseRequest(DsarChannel.EMAIL, PROPOSER, NOW, DsarStatus.IN_PROGRESS));

        assertThatThrownBy(() -> service.approve(APPROVER, REQUEST_ID, "Khong du quyen duyet", RequestEvidence.system()))
                .isInstanceOf(PermissionDeniedException.class);
        verify(erasureCoordinator, never()).erase(any());
    }

    @Test
    void anAdminEnteredRequestWithoutAKnownProposerIsRefused() {
        givenRole(APPROVER, "DPO");
        givenRequest(eraseRequest(DsarChannel.POST, null, NOW, DsarStatus.IN_PROGRESS));

        assertThatThrownBy(() -> service.approve(APPROVER, REQUEST_ID, "Khong ro ai de xuat", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.VALIDATION_FAILED);
        verify(erasureCoordinator, never()).erase(any());
    }

    @Test
    void selfServiceErasureNeedsNoSecondProposerBecauseTheSubjectIsTheProposer() {
        givenRole(APPROVER, "DPO");
        // handledBy = chính người duyệt: ở luồng tự phục vụ đó chỉ là "ai đang xử lý hồ sơ".
        givenRequest(eraseRequest(DsarChannel.SELF_SERVICE, APPROVER, NOW, DsarStatus.IN_PROGRESS));

        service.approve(APPROVER, REQUEST_ID, "Chu the tu yeu cau trong app", RequestEvidence.system());

        verify(erasureCoordinator).erase(SUBJECT);
    }

    @Test
    void erasureIsRefusedWhileIdentityIsNotVerified() {
        givenRole(APPROVER, "DPO");
        givenRequest(eraseRequest(DsarChannel.EMAIL, PROPOSER, null, DsarStatus.IDENTITY_PENDING));

        assertThatThrownBy(() -> service.approve(APPROVER, REQUEST_ID, "Chua xac minh danh tinh", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED);
        verify(erasureCoordinator, never()).erase(any());
    }

    @Test
    void anAccessExportRequestCannotBeApprovedAsAnErasure() {
        givenRole(APPROVER, "DPO");
        when(dsarPort.findById(REQUEST_ID)).thenReturn(Optional.of(new DsarRequest(
                REQUEST_ID, "DSAR-2026-000009", SUBJECT, "a@b.test", DsarRequestType.ACCESS_EXPORT,
                DsarChannel.EMAIL, DsarStatus.IN_PROGRESS, NOW, "ID_DOC_MANUAL", NOW,
                NOW.plusSeconds(172_800), null, NOW.plusSeconds(864_000), null, null, false,
                null, null, null, null, null, PROPOSER, null, NOW)));

        assertThatThrownBy(() -> service.approve(APPROVER, REQUEST_ID, "Sai loai yeu cau", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class);
        verify(erasureCoordinator, never()).erase(any());
    }

    @Test
    void anAlreadyCompletedRequestIsNotErasedTwice() {
        givenRole(APPROVER, "DPO");
        givenRequest(eraseRequest(DsarChannel.EMAIL, PROPOSER, NOW, DsarStatus.COMPLETED));

        assertThatThrownBy(() -> service.approve(APPROVER, REQUEST_ID, "Da hoan tat tu truoc", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class);
        verify(erasureCoordinator, never()).erase(any());
    }

    private void givenRole(UUID userId, String role) {
        when(userAccountPort.snapshot(userId)).thenReturn(new UserAccountSnapshot(
                userId, "staff@catcheck.vn", "ACTIVE", null, null, null, null, Set.of(role)));
    }

    private void givenRequest(DsarRequest request) {
        when(dsarPort.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    }

    private static DsarRequest eraseRequest(DsarChannel channel, UUID handledBy,
                                            Instant identityVerifiedAt, DsarStatus status) {
        return new DsarRequest(REQUEST_ID, "DSAR-2026-000010", SUBJECT, "subject@catcheck.test",
                DsarRequestType.ERASE, channel, status, identityVerifiedAt,
                identityVerifiedAt == null ? null : "ID_DOC_MANUAL", NOW, NOW.plusSeconds(172_800),
                null, NOW.plusSeconds(1_728_000), null, null, false, null, null, null, null, null,
                handledBy, null, NOW);
    }
}
