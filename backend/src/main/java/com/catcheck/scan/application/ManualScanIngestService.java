package com.catcheck.scan.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ManualScanIngestService {

    private static final byte[] NO_IMAGE = new byte[] {0};

    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ScanPersistenceService persistenceService;
    private final ManualPipelineOutcomeFactory outcomeFactory;
    private final CatOwnershipPort catOwnershipPort;
    private final CreditConsumption creditConsumption;
    private final Clock clock;

    public ManualScanIngestService(ScanRepository scanRepository,
                                   ScanAnalysisRepository scanAnalysisRepository,
                                   ScanPersistenceService persistenceService,
                                   ManualPipelineOutcomeFactory outcomeFactory,
                                   CatOwnershipPort catOwnershipPort,
                                   CreditConsumption creditConsumption,
                                   Clock clock) {
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.persistenceService = persistenceService;
        this.outcomeFactory = outcomeFactory;
        this.catOwnershipPort = catOwnershipPort;
        this.creditConsumption = creditConsumption;
        this.clock = clock;
    }

    public ScanSubmitResult ingest(ManualScanCommand cmd) {
        Optional<ScanSubmitResult> replay = tryReplay(cmd.userId(), cmd.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get();
        }

        validate(cmd);

        CatOwnershipPort.CatSnapshot cat = null;
        if (cmd.assignment() == ScanAssignment.ASSIGNED) {
            cat = catOwnershipPort.findSnapshot(cmd.catId())
                    .orElseThrow(() -> new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED));
            if (!cat.isOwnedBy(cmd.userId())) {
                throw new PermissionDeniedException(ScanErrorCode.CAT_NOT_OWNED);
            }
            if (cat.archived()) {
                throw new ConflictException(CatErrorCode.CAT_ARCHIVED);
            }
        }

        PipelineOutcome outcome = outcomeFactory.fromManualPh(
                cmd.phValue(), cmd.forcedClassification(), cmd.confidence(), cmd.qualityFlags());

        boolean trialRegime = creditConsumption.hasTrialScanRemaining(cmd.userId());
        if (!outcome.inconclusive() && !trialRegime && !creditConsumption.hasAvailableCredit(cmd.userId())) {
            throw new BusinessRuleException(CreditErrorCode.CREDIT_INSUFFICIENT, 0, 1);
        }

        SubmitScanCommand command = new SubmitScanCommand(
                cmd.userId(), cmd.catId(), cmd.assignment(), cmd.capturedAt(), cmd.captureSource(),
                cmd.deviceHint(), cmd.idempotencyKey(), null, null, NO_IMAGE);

        return persistenceService.persist(command, outcome, cat, trialRegime, false, false, Optional.empty());
    }

    private Optional<ScanSubmitResult> tryReplay(UUID userId, String idempotencyKey) {
        return scanRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(existing -> {
                    ScanAnalysis analysis = scanAnalysisRepository.findCurrentByScanId(existing.getId())
                            .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
                    CatOwnershipPort.CatSnapshot cat = existing.getCatId() == null
                            ? null
                            : catOwnershipPort.findSnapshot(existing.getCatId()).orElse(null);
                    return persistenceService.replay(existing, analysis, cat);
                });
    }

    private void validate(ManualScanCommand cmd) {
        ScanAssignment assignment = cmd.assignment();
        if (assignment == null || assignment == ScanAssignment.UNASSIGNED) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (assignment == ScanAssignment.ASSIGNED && cmd.catId() == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (assignment == ScanAssignment.SHARED_UNKNOWN && cmd.catId() != null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        Instant capturedAt = cmd.capturedAt();
        if (capturedAt == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (capturedAt.isAfter(clock.instant().plusSeconds(2 * 3600L))) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
    }
}
