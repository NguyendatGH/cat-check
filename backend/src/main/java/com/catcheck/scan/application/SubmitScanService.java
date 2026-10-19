package com.catcheck.scan.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.api.EntitlementQuery;
import com.catcheck.credit.api.EntitlementQuery.Feature;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StoredImage;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.application.spi.ImageRetentionConsentPort;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SubmitScanService {

    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ColorPipelineService pipelineService;
    private final ScanPipelineMetrics pipelineMetrics;
    private final ScanPersistenceService persistenceService;
    private final CatOwnershipPort catOwnershipPort;
    private final CreditConsumption creditConsumption;
    private final EntitlementQuery entitlementQuery;
    private final ImageRetentionConsentPort imageRetentionConsent;
    private final ImageStorage imageStorage;
    private final Clock clock;

    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public SubmitScanService(
            ScanRepository scanRepository,
            ScanAnalysisRepository scanAnalysisRepository,
            ColorPipelineService pipelineService,
            ScanPipelineMetrics pipelineMetrics,
            ScanPersistenceService persistenceService,
            CatOwnershipPort catOwnershipPort,
            CreditConsumption creditConsumption,
            EntitlementQuery entitlementQuery,
            ImageRetentionConsentPort imageRetentionConsent,
            ImageStorage imageStorage,
            Clock clock) {
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.pipelineService = pipelineService;
        this.pipelineMetrics = pipelineMetrics;
        this.persistenceService = persistenceService;
        this.catOwnershipPort = catOwnershipPort;
        this.creditConsumption = creditConsumption;
        this.entitlementQuery = entitlementQuery;
        this.imageRetentionConsent = imageRetentionConsent;
        this.imageStorage = imageStorage;
        this.clock = clock;
    }

    public ScanSubmitResult submit(SubmitScanCommand cmd) {
        Optional<ScanSubmitResult> replay = tryReplay(cmd);
        if (replay.isPresent()) {
            return replay.get();
        }

        String inFlightKey = cmd.userId() + ":" + cmd.idempotencyKey();
        if (inFlight.putIfAbsent(inFlightKey, Boolean.TRUE) != null) {
            throw new ConflictException(ScanErrorCode.SCAN_IN_PROGRESS);
        }
        try {
            Optional<ScanSubmitResult> raced = tryReplay(cmd);
            if (raced.isPresent()) {
                return raced.get();
            }
            return doSubmit(cmd);
        } finally {
            inFlight.remove(inFlightKey);
        }
    }

    private Optional<ScanSubmitResult> tryReplay(SubmitScanCommand cmd) {
        return scanRepository.findByUserIdAndIdempotencyKey(cmd.userId(), cmd.idempotencyKey())
                .map(existing -> {
                    ScanAnalysis analysis = scanAnalysisRepository.findCurrentByScanId(existing.getId())
                            .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
                    CatOwnershipPort.CatSnapshot cat = existing.getCatId() == null
                            ? null
                            : catOwnershipPort.findSnapshot(existing.getCatId()).orElse(null);
                    return persistenceService.replay(existing, analysis, cat);
                });
    }

    private ScanSubmitResult doSubmit(SubmitScanCommand cmd) {
        validateMetadata(cmd);

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

        boolean trialRegime = creditConsumption.hasTrialScanRemaining(cmd.userId());
        boolean creditAvailable = creditConsumption.hasAvailableCredit(cmd.userId());
        if (!trialRegime && !creditAvailable) {
            throw new BusinessRuleException(CreditErrorCode.CREDIT_INSUFFICIENT, 0, 1);
        }

        PipelineOutcome outcome = pipelineMetrics.recordPipelineCall(
                () -> pipelineService.analyze(cmd.imageBytes(), cmd.roi(), cmd.cardQuadHint()));

        boolean storeImageAllowed = entitlementQuery.isFeatureEnabled(cmd.userId(), Feature.STORE_IMAGE);
        boolean imageConsentGranted = storeImageAllowed && imageRetentionConsent.isGranted(cmd.userId());
        boolean willStoreImage = imageConsentGranted && !trialRegime && !outcome.inconclusive();

        Optional<StoredImage> storedImage = Optional.empty();
        if (willStoreImage) {
            storedImage = Optional.of(imageStorage.put("scan-image", new ImageUpload(
                    new ByteArrayInputStream(cmd.imageBytes()), "scan.jpg",
                    outcome.imageContentType(), cmd.imageBytes().length)));
        }

        try {
            return persistenceService.persist(cmd, outcome, cat, trialRegime, storeImageAllowed,
                    imageConsentGranted, storedImage);
        } catch (RuntimeException ex) {
            storedImage.ifPresent(stored -> safeDelete(stored));
            throw ex;
        }
    }

    private void safeDelete(StoredImage stored) {
        try {
            imageStorage.delete(stored.key());
        } catch (RuntimeException ignored) {
        }
    }

    private void validateMetadata(SubmitScanCommand cmd) {
        if (cmd.assignment() == ScanAssignment.UNASSIGNED) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.assignment() == ScanAssignment.ASSIGNED && cmd.catId() == null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.assignment() == ScanAssignment.SHARED_UNKNOWN && cmd.catId() != null) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        Instant now = clock.instant();
        if (cmd.capturedAt().isAfter(now.plusSeconds(2 * 3600L))) {
            throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
        }
        if (cmd.imageBytes().length > ScanThresholds.MAX_UPLOAD_BYTES) {
            throw new BusinessRuleException(ScanErrorCode.IMAGE_TOO_LARGE,
                    cmd.imageBytes().length, ScanThresholds.MAX_UPLOAD_BYTES);
        }
    }
}
