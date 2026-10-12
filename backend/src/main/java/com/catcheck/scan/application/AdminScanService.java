package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.api.ScanReassignedEvent;
import com.catcheck.scan.application.spi.OpenDsarPort;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanReassignment;
import com.catcheck.scan.domain.ScanStatus;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanQueryRepository;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminScanService {

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private static final String ACTION_IMAGE_VIEW = "ADMIN_SCAN_IMAGE_VIEW";

    private static final Logger log = LoggerFactory.getLogger(AdminScanService.class);

    private final ScanRepository scanRepository;
    private final ScanQueryRepository scanQueryRepository;
    private final ScanReassignmentRepository reassignmentRepository;
    private final ScanImageAccessService imageAccessService;
    private final CatOwnershipPort catOwnershipPort;
    private final OpenDsarPort openDsarPort;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public AdminScanService(ScanRepository scanRepository,
                            ScanQueryRepository scanQueryRepository,
                            ScanReassignmentRepository reassignmentRepository,
                            ScanImageAccessService imageAccessService,
                            CatOwnershipPort catOwnershipPort,
                            OpenDsarPort openDsarPort,
                            AuditLogService auditLogService,
                            ApplicationEventPublisher eventPublisher,
                            UuidV7 uuidV7,
                            Clock clock) {
        this.scanRepository = scanRepository;
        this.scanQueryRepository = scanQueryRepository;
        this.reassignmentRepository = reassignmentRepository;
        this.imageAccessService = imageAccessService;
        this.catOwnershipPort = catOwnershipPort;
        this.openDsarPort = openDsarPort;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }


    @Transactional
    public AdminScanPage listUserScans(UUID userId, int page, int size, AdminActionContext context) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        boolean fullData = hasDsarFullAccess(userId, context);

        ScanQueryRepository.OffsetPage result =
                scanQueryRepository.findByUserForAdmin(userId, safePage * safeSize, safeSize);

        auditLogService.record(base(context, "ADMIN_USER_SCANS_VIEW", AuditSubjectType.SCAN, userId)
                .meta("page", safePage)
                .meta("size", safeSize)
                .meta("resultCount", result.items().size())
                .meta("unmaskedByDsar", fullData)
                .build());

        return new AdminScanPage(result.items(), result.totalElements(), safePage, safeSize, fullData);
    }


    public ScanImageAccessService.Result openImageForDpo(UUID scanId, AdminActionContext context) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));

        if (!openDsarPort.hasOpenRequestFor(scan.getUserId())) {
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .outcome(AuditOutcome.DENIED)
                    .meta("scanId", scanId)
                    .meta("denyReason", "NO_OPEN_DSAR")
                    .build());
            throw new PermissionDeniedException(ScanErrorCode.SCAN_IMAGE_DSAR_REQUIRED);
        }

        try {
            ScanImageAccessService.Result result = imageAccessService.openForDsar(scan);
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .meta("scanId", scanId)
                    .build());
            log.info("DPO mở ảnh scan: scanId={} adminId={}", scanId, context.adminId());
            return result;
        } catch (NotFoundException | ConflictException ex) {
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .outcome(AuditOutcome.ERROR)
                    .meta("scanId", scanId)
                    .meta("denyReason", ex.errorCode().code())
                    .build());
            throw ex;
        }
    }


    @Transactional
    public AdminReassignResult reassign(UUID scanId, UUID toCatId, boolean toShared,
                                        AdminActionContext context) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        Instant now = clock.instant();

        if (scan.getDeletedAt() != null || scan.getStatus() != ScanStatus.ANALYZED) {
            throw new ConflictException(ScanErrorCode.SCAN_NOT_REASSIGNABLE);
        }

        UUID fromCatId = scan.getCatId();
        ScanAssignment fromAssignment = scan.getAssignment();
        ScanAssignment toAssignment;
        UUID targetCatId = toCatId;

        if (toShared) {
            toAssignment = ScanAssignment.SHARED_UNKNOWN;
            targetCatId = null;
        } else {
            if (targetCatId == null) {
                throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
            }
            if (targetCatId.equals(fromCatId)) {
                throw new ConflictException(ScanErrorCode.SCAN_REASSIGN_SAME_TARGET);
            }
            CatOwnershipPort.CatSnapshot target = catOwnershipPort.findSnapshot(targetCatId)
                    .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
            if (!target.isOwnedBy(scan.getUserId())) {
                throw new NotFoundException(CatErrorCode.CAT_NOT_FOUND);
            }
            toAssignment = ScanAssignment.ASSIGNED;
        }

        scan.reassignTo(targetCatId, toAssignment, now);
        scanRepository.save(scan);

        reassignmentRepository.save(new ScanReassignment(
                uuidV7.generate(), scanId, fromCatId, targetCatId, fromAssignment, toAssignment,
                context.adminId(), context.adminRole(), context.reason(), now));

        eventPublisher.publishEvent(new ScanReassignedEvent(scanId, fromCatId, targetCatId));

        auditLogService.record(base(context, "ADMIN_SCAN_REASSIGNED", AuditSubjectType.SCAN,
                scan.getUserId())
                .meta("scanId", scanId)
                .before(java.util.Map.of(
                        "catId", fromCatId == null ? "null" : fromCatId,
                        "assignment", fromAssignment.name()))
                .after(java.util.Map.of(
                        "catId", targetCatId == null ? "null" : targetCatId,
                        "assignment", toAssignment.name()))
                .build());

        return new AdminReassignResult(scanId, fromCatId, targetCatId,
                toAssignment.name(), scan.getReassignCount());
    }


    private boolean hasDsarFullAccess(UUID userId, AdminActionContext context) {
        return context.isDpo() && openDsarPort.hasOpenRequestFor(userId);
    }

    private AuditEvent.Builder base(AdminActionContext context, String action,
                                    AuditSubjectType subjectType, UUID subjectUserId) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(context.auditActor())
                .subject(subjectType, subjectUserId)
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent());
        if (context.reason() != null) {
            builder.meta("reason", context.reason());
        }
        return builder;
    }

    public record AdminScanPage(
            List<ScanQueryRepository.Row> rows,
            long totalElements,
            int page,
            int size,
            boolean fullData
    ) {

        public AdminScanPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    public record AdminReassignResult(
            UUID scanId, UUID fromCatId, UUID toCatId, String toAssignment, short reassignCount) {
    }
}
