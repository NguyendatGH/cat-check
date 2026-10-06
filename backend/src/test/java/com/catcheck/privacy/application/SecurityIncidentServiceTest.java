package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L60/L61 — hồ sơ sự cố ({@code security_incident}, p15 §15.9.5 + REQ-INC-01/02).
 *
 * <p>Ba thứ phải đúng vì chúng là nghĩa vụ pháp lý có thời hạn, không phải tiện ích UI:
 * {@code detected_at} (khởi động đồng hồ 72 giờ của Điều 23.1 Luật BVDLCN),
 * {@code retain_until} ({@code resolved_at + 5 năm}, Đ29.1.c NĐ356) và tính đơn điệu của các
 * mốc (một mốc nằm trước lúc phát hiện làm mọi phép đo SLA sai).</p>
 */
class SecurityIncidentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID DPO_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6610");
    private static final UUID INCIDENT_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6611");

    private final SecurityIncidentPort incidentPort = mock(SecurityIncidentPort.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final SecurityIncidentService service = new SecurityIncidentService(
            incidentPort, auditLogService, new UuidV7(Clock.fixed(NOW, ZoneOffset.UTC)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void openingAnIncidentKeepsTheReportedDetectionTimeAndAudits() {
        Instant detected = NOW.minusSeconds(7200);
        when(incidentPort.save(any())).thenAnswer(call -> call.getArgument(0));

        SecurityIncident saved = service.open(DPO_ID, command(IncidentSeverity.HIGH, detected),
                RequestEvidence.system());

        assertThat(saved.detectedAt())
                .as("đồng hồ 72 giờ chạy từ lúc PHÁT HIỆN, không từ lúc có người nhập hồ sơ")
                .isEqualTo(detected);
        assertThat(SecurityIncidentService.authorityDeadline(saved))
                .isEqualTo(detected.plusSeconds(72 * 3600));
        assertThat(saved.handledBy()).isEqualTo(DPO_ID);

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY_INCIDENT_OPENED");
        assertThat(audit.getValue().metadata()).containsEntry("severity", "HIGH");
    }

    @Test
    void aDetectionTimeInTheFutureIsRefused() {
        assertThatThrownBy(() -> service.open(DPO_ID,
                command(IncidentSeverity.LOW, NOW.plusSeconds(60)), RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.VALIDATION_FAILED);
        verify(incidentPort, never()).save(any());
    }

    @Test
    void resolvingAnIncidentComputesRetainUntilFiveYearsLater() {
        Instant detected = NOW.minusSeconds(86_400);
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(incident(detected, null)));

        SecurityIncident updated = service.patch(DPO_ID, INCIDENT_ID,
                patch(null, NOW), RequestEvidence.system());

        assertThat(updated.resolvedAt()).isEqualTo(NOW);
        assertThat(updated.retainUntil())
                .as("Đ29.1.c NĐ356: hồ sơ giữ ≥ 5 năm kể từ ngày khắc phục xong")
                .isEqualTo(NOW.plus(5L * 365, ChronoUnit.DAYS));
        verify(incidentPort).update(any());
    }

    @Test
    void recordingTheAuthorityNotificationClearsTheOverdueFlag() {
        Instant detected = NOW.minusSeconds(100 * 3600);
        SecurityIncident stale = incident(detected, null);
        assertThat(stale.isAuthorityNotificationOverdue(NOW))
                .as("HIGH quá 72 giờ mà chưa báo A05 ⇒ quá hạn (REQ-INC-02)")
                .isTrue();
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(stale));

        SecurityIncident updated = service.patch(DPO_ID, INCIDENT_ID,
                patch(NOW.minusSeconds(3600), null), RequestEvidence.system());

        assertThat(updated.isAuthorityNotificationOverdue(NOW)).isFalse();
    }

    @Test
    void aMilestoneBeforeDetectionIsRefused() {
        Instant detected = NOW.minusSeconds(3600);
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(incident(detected, null)));

        assertThatThrownBy(() -> service.patch(DPO_ID, INCIDENT_ID,
                patch(detected.minusSeconds(1), null), RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class);
        verify(incidentPort, never()).update(any());
    }

    @Test
    void patchingAnUnknownIncidentIs404() {
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.patch(DPO_ID, INCIDENT_ID, patch(null, NOW), RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND);
        assertThat(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND.status().value()).isEqualTo(404);
    }

    @Test
    void anAbsentFieldLeavesTheStoredValueAlone() {
        Instant detected = NOW.minusSeconds(3600);
        SecurityIncident current = incident(detected, NOW.minusSeconds(120));
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(current));

        SecurityIncident updated = service.patch(DPO_ID, INCIDENT_ID,
                new SecurityIncidentService.Patch(null, null, null, null, null, null, null, null,
                        null, null, null, "Chi doi nguoi xu ly"),
                RequestEvidence.system());

        assertThat(updated.severity()).isEqualTo(current.severity());
        assertThat(updated.summary()).isEqualTo(current.summary());
        assertThat(updated.containedAt()).isEqualTo(current.containedAt());
    }

    private static SecurityIncidentService.Command command(IncidentSeverity severity, Instant detectedAt) {
        return new SecurityIncidentService.Command(severity, IncidentCategory.DATA_BREACH,
                "Ro bang spring_session qua ban sao luu", 120, List.of("D1", "D18"), detectedAt,
                null, "Mo ho so theo quy trinh 15.9.5");
    }

    private static SecurityIncidentService.Patch patch(Instant authorityNotifiedAt, Instant resolvedAt) {
        return new SecurityIncidentService.Patch(null, null, null, null, null, null, null,
                authorityNotifiedAt, null, resolvedAt, null, "Cap nhat moc theo quy trinh");
    }

    private static SecurityIncident incident(Instant detectedAt, Instant containedAt) {
        return new SecurityIncident(INCIDENT_ID, "INC-2026-000001", IncidentSeverity.HIGH,
                IncidentCategory.DATA_BREACH, "Ro du lieu", 120, List.of("D1"), detectedAt,
                null, containedAt, null, null, null, null, DPO_ID, null, detectedAt);
    }
}
