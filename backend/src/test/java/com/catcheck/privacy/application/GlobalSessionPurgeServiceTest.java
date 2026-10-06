package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.application.export.DsarExportJobPort;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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
 * L62 — runbook R1 (p11 §11.13.4).
 *
 * <p>Hai khẳng định quan trọng nhất: (1) {@code incidentId} không trỏ tới hồ sơ thật thì
 * <b>không xoá gì cả</b> — hành động này đăng xuất mọi người dùng nên không được có đường
 * "purge trước, điền hồ sơ sau"; (2) audit ghi đủ {@code revokedCount}, {@code reason},
 * {@code incidentId} đúng như §11.13.4 đòi.</p>
 */
class GlobalSessionPurgeServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-06T03:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6620");
    private static final UUID INCIDENT_ID = UUID.fromString("0199a1c4-3f7b-7d21-9c88-3ae51f2b6621");

    private final GlobalSessionPurgePort purgePort = mock(GlobalSessionPurgePort.class);
    private final SecurityIncidentPort incidentPort = mock(SecurityIncidentPort.class);
    private final DsarExportJobPort dsarExportJobPort = mock(DsarExportJobPort.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GlobalSessionPurgeService service = new GlobalSessionPurgeService(
            purgePort,
            new SecurityIncidentService(incidentPort, mock(AuditLogService.class), new UuidV7(clock), clock),
            dsarExportJobPort, auditLogService, clock);

    @Test
    void purgeRevokesEverySessionAndRecordsTheRunbookAudit() {
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(incident()));
        when(purgePort.purgeEverySession())
                .thenReturn(new GlobalSessionPurgePort.PurgeOutcome(812, 804, 17));
        when(dsarExportJobPort.revokeAllDownloadLinks(NOW)).thenReturn(3);

        GlobalSessionPurgeService.Result result = service.purgeAll(
                ADMIN_ID, "ADMIN_SUPER", INCIDENT_ID, "Ro bang spring_session, chay R1",
                RequestEvidence.system());

        assertThat(result.revokedCount()).isEqualTo(812);
        assertThat(result.otpInvalidated()).isEqualTo(17);
        assertThat(result.dsarDownloadLinksRevoked()).isEqualTo(3);
        assertThat(result.incidentRef()).isEqualTo("INC-2026-000001");

        ArgumentCaptor<AuditEvent> audit = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("SECURITY.SESSIONS_PURGED_ALL");
        assertThat(audit.getValue().metadata())
                .containsEntry("revokedCount", 812)
                .containsEntry("incidentId", INCIDENT_ID)
                .containsEntry("reason", "Ro bang spring_session, chay R1");
    }

    @Test
    void anUnknownIncidentIdPurgesNothing() {
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.purgeAll(ADMIN_ID, "ADMIN_SUPER", INCIDENT_ID,
                "Khong co ho so su co nao", RequestEvidence.system()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND);
        verify(purgePort, never()).purgeEverySession();
        verify(dsarExportJobPort, never()).revokeAllDownloadLinks(any());
        verify(auditLogService, never()).record(any());
    }

    @Test
    void dsarDownloadLinksDieBeforeSessionsDo() {
        when(incidentPort.findById(INCIDENT_ID)).thenReturn(Optional.of(incident()));
        when(purgePort.purgeEverySession()).thenReturn(GlobalSessionPurgePort.PurgeOutcome.none());

        service.purgeAll(ADMIN_ID, "ADMIN_SUPER", INCIDENT_ID, "Kiem thu tu thao tac",
                RequestEvidence.system());

        // Nếu transaction vỡ ở giữa, trạng thái còn lại phải là "link đã chết, phiên còn sống",
        // không phải chiều ngược lại.
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(dsarExportJobPort, purgePort);
        order.verify(dsarExportJobPort).revokeAllDownloadLinks(NOW);
        order.verify(purgePort).purgeEverySession();
    }

    private static SecurityIncident incident() {
        return new SecurityIncident(INCIDENT_ID, "INC-2026-000001", IncidentSeverity.CRITICAL,
                IncidentCategory.DATA_BREACH, "Ro DB", 1000, List.of("D1"),
                NOW.minusSeconds(3600), null, null, null, null, null, null, ADMIN_ID, null,
                NOW.minusSeconds(3600));
    }
}
