package com.catcheck.community.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.domain.CommunityReport;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CommunityModerationService {
    private static final List<String> ACTIONS = List.of("HIDE_POST", "REMOVE_POST", "HIDE_COMMENT", "DISMISS");

    private final CommunityRepository repository;
    private final AuditLogService auditLogService;

    public CommunityModerationService(CommunityRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public Page list(String status, int page, int size) {
        String normalized = status == null || status.isBlank() ? "OPEN" : status.strip().toUpperCase(Locale.ROOT);
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);
        return new Page(repository.findReports(normalized, safePage * safeSize, safeSize), safePage, safeSize,
                repository.countReports(normalized));
    }

    @Transactional
    public CommunityReport moderate(UUID reportId, String action, String reason, ModerationContext context) {
        CommunityReport report = repository.findReport(reportId)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.REPORT_NOT_FOUND));
        String normalizedAction = action == null ? "" : action.strip().toUpperCase(Locale.ROOT);
        if (!ACTIONS.contains(normalizedAction)) {
            throw new BusinessRuleException(CommunityErrorCode.MODERATION_ACTION_INVALID, normalizedAction);
        }
        repository.moderateReport(reportId, normalizedAction);
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(context.adminId(), context.role()))
                .subject(AuditSubjectType.ARTICLE, report.targetId())
                .action("COMMUNITY_REPORT_MODERATE")
                .outcome(AuditOutcome.SUCCESS)
                .meta("reportId", reportId)
                .meta("targetType", report.targetType())
                .meta("action", normalizedAction)
                .meta("reason", reason)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
        return repository.findReport(reportId).orElseThrow();
    }

    public record Page(List<CommunityReport> items, int page, int size, long totalElements) {
        public int totalPages() { return size == 0 ? 0 : (int) Math.ceilDiv(totalElements, size); }
        public boolean hasMore() { return (long) (page + 1) * size < totalElements; }
    }

    public record ModerationContext(UUID adminId, String role, String requestId, String ipAddress, String userAgent) { }
}
