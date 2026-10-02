package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.api.ScanReassignedEvent;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.ScanImageDeleteReason;
import com.catcheck.scan.domain.ScanReassignment;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanImageRepository;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code DELETE /scans/{id}}, {@code POST /scans/{id}/reassign-cat},
 * {@code POST}/{@code DELETE /scans/{id}/dispute} (p8 §8.4.5, p6 §6.10.3).
 */
@Service
public class ScanLifecycleService {

    private final ScanRepository scanRepository;
    private final ScanImageRepository scanImageRepository;
    private final ScanReassignmentRepository reassignmentRepository;
    private final CatOwnershipPort catOwnershipPort;
    private final ImageStorage imageStorage;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ScanLifecycleService(ScanRepository scanRepository, ScanImageRepository scanImageRepository,
                                 ScanReassignmentRepository reassignmentRepository,
                                 CatOwnershipPort catOwnershipPort, ImageStorage imageStorage,
                                 AuditLogService auditLogService, ApplicationEventPublisher eventPublisher,
                                 UuidV7 uuidV7, Clock clock) {
        this.scanRepository = scanRepository;
        this.scanImageRepository = scanImageRepository;
        this.reassignmentRepository = reassignmentRepository;
        this.catOwnershipPort = catOwnershipPort;
        this.imageStorage = imageStorage;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public void delete(UUID userId, UUID scanId) {
        Scan scan = requireOwned(userId, scanId);
        if (scan.getDeletedAt() != null) {
            throw new ConflictException(ScanErrorCode.SCAN_ALREADY_DELETED);
        }
        Instant now = clock.instant();
        scan.softDelete(now);
        scanRepository.save(scan);

        // Xoá cứng file ảnh (p8 E8: "xoá mềm scan + xoá cứng file ảnh"), giữ tombstone scan_image.
        scanImageRepository.findByScanId(scanId).ifPresent(image -> {
            if (image.getDeletedAt() == null && image.getStorageKey() != null) {
                imageStorage.delete(StorageKey.parse(image.getStorageKey()));
                image.markDeleted(ScanImageDeleteReason.USER_REQUEST, now);
                scanImageRepository.save(image);
            }
        });

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.user(userId, "USER"))
                .subject(AuditSubjectType.SCAN, userId)
                .action("SCAN.DELETED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("scanId", scanId.toString())
                .build());
    }

    public record ReassignResult(UUID scanId, UUID fromCatId, UUID toCatId, int reassignRemaining) {
    }

    @Transactional
    public ReassignResult reassign(UUID userId, UUID scanId, UUID toCatId, boolean toShared, String reason) {
        Scan scan = requireOwned(userId, scanId);
        Instant now = clock.instant();

        if (scan.getDeletedAt() != null || scan.getStatus() != com.catcheck.scan.domain.ScanStatus.ANALYZED) {
            throw new ConflictException(ScanErrorCode.SCAN_NOT_REASSIGNABLE);
        }
        boolean windowOpen = scan.getCapturedAt()
                .plusSeconds(com.catcheck.scan.domain.ScanThresholds.REASSIGN_WINDOW_HOURS * 3600L)
                .isAfter(now);
        if (!windowOpen) {
            throw new ConflictException(ScanErrorCode.SCAN_REASSIGN_WINDOW_CLOSED);
        }
        if (scan.getReassignCount() >= com.catcheck.scan.domain.ScanThresholds.REASSIGN_MAX_COUNT) {
            throw new ConflictException(ScanErrorCode.SCAN_REASSIGN_LIMIT_REACHED);
        }

        UUID fromCatId = scan.getCatId();
        ScanAssignment fromAssignment = scan.getAssignment();
        ScanAssignment toAssignment;

        if (toShared) {
            toAssignment = ScanAssignment.SHARED_UNKNOWN;
            toCatId = null;
        } else {
            if (toCatId == null) {
                throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
            }
            if (toCatId.equals(fromCatId)) {
                throw new ConflictException(ScanErrorCode.SCAN_REASSIGN_SAME_TARGET);
            }
            CatOwnershipPort.CatSnapshot target = catOwnershipPort.findSnapshot(toCatId)
                    .orElseThrow(() -> new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED));
            if (!target.isOwnedBy(userId)) {
                throw new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED);
            }
            toAssignment = ScanAssignment.ASSIGNED;
        }

        scan.reassignTo(toCatId, toAssignment, now);
        scanRepository.save(scan);

        ScanReassignment log = new ScanReassignment(
                uuidV7.generate(), scanId, fromCatId, toCatId, fromAssignment, toAssignment,
                userId, "USER", reason, now);
        reassignmentRepository.save(log);

        eventPublisher.publishEvent(new ScanReassignedEvent(scanId, fromCatId, toCatId));

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.user(userId, "USER"))
                .subject(AuditSubjectType.SCAN, userId)
                .action("SCAN.REASSIGNED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("scanId", scanId.toString())
                .build());

        int remaining = com.catcheck.scan.domain.ScanThresholds.REASSIGN_MAX_COUNT - scan.getReassignCount();
        return new ReassignResult(scanId, fromCatId, toCatId, remaining);
    }

    @Transactional
    public Instant dispute(UUID userId, UUID scanId, String note) {
        Scan scan = requireOwned(userId, scanId);
        if (scan.getDisputedAt() != null) {
            throw new ConflictException(ScanErrorCode.SCAN_ALREADY_DISPUTED);
        }
        Instant now = clock.instant();
        scan.dispute(note, now);
        scanRepository.save(scan);
        return now;
    }

    @Transactional
    public void clearDispute(UUID userId, UUID scanId) {
        Scan scan = requireOwned(userId, scanId);
        if (scan.getDisputedAt() == null) {
            throw new NotFoundException(ScanErrorCode.SCAN_DISPUTE_NOT_FOUND);
        }
        scan.clearDispute(clock.instant());
        scanRepository.save(scan);
    }

    private Scan requireOwned(UUID userId, UUID scanId) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        if (!scan.getUserId().equals(userId)) {
            throw new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND);
        }
        return scan;
    }
}
