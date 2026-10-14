package com.catcheck.community.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.community.domain.CommunityReport;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommunityModerationServiceTest {

    private static final CommunityModerationService.ModerationContext CONTEXT =
            new CommunityModerationService.ModerationContext(UUID.randomUUID(), "MODERATOR", null, null, null);

    @Test
    void commentReportRejectsPostActionsAndLeavesReportOpen() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityReport report = report(null, UUID.randomUUID(), "OPEN");
        when(repository.findReport(report.id())).thenReturn(Optional.of(report));
        CommunityModerationService service = new CommunityModerationService(repository, mock(AuditLogService.class));

        assertThatThrownBy(() -> service.moderate(report.id(), "HIDE_POST", "Nội dung vi phạm quy tắc", CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).moderateReport(any(), anyString());
    }

    @Test
    void closedReportCannotBeModeratedAgain() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityReport report = report(UUID.randomUUID(), null, "RESOLVED");
        when(repository.findReport(report.id())).thenReturn(Optional.of(report));
        CommunityModerationService service = new CommunityModerationService(repository, mock(AuditLogService.class));

        assertThatThrownBy(() -> service.moderate(report.id(), "REMOVE_POST", "Nội dung vi phạm quy tắc", CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
        verify(repository, never()).moderateReport(any(), anyString());
    }

    @Test
    void matchingActionOnOpenReportIsApplied() {
        CommunityRepository repository = mock(CommunityRepository.class);
        CommunityReport report = report(null, UUID.randomUUID(), "OPEN");
        when(repository.findReport(report.id())).thenReturn(Optional.of(report));
        CommunityModerationService service = new CommunityModerationService(repository, mock(AuditLogService.class));

        service.moderate(report.id(), "HIDE_COMMENT", "Nội dung vi phạm quy tắc", CONTEXT);

        verify(repository).moderateReport(report.id(), "HIDE_COMMENT");
    }

    private static CommunityReport report(UUID postId, UUID commentId, String status) {
        return new CommunityReport(UUID.randomUUID(), postId, commentId, "Reporter", "SPAM", null, status,
                "Tiêu đề", Instant.parse("2026-10-01T00:00:00Z"));
    }
}
