package com.catcheck.scan.application;

import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import com.catcheck.scan.domain.port.ScanQueryRepository;
import com.catcheck.scan.domain.port.ScanQueryRepository.HistoryFilter;
import com.catcheck.scan.domain.port.ScanQueryRepository.Page;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;
import com.catcheck.scan.domain.port.ScanQueryRepository.Summary;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ScanQueryService {

    private final ScanQueryRepository queryRepository;
    private final ScanRepository scanRepository;
    private final ScanAnalysisRepository scanAnalysisRepository;
    private final ScanReassignmentRepository reassignmentRepository;
    private final CatOwnershipPort catOwnershipPort;

    public ScanQueryService(ScanQueryRepository queryRepository, ScanRepository scanRepository,
                             ScanAnalysisRepository scanAnalysisRepository,
                             ScanReassignmentRepository reassignmentRepository,
                             CatOwnershipPort catOwnershipPort) {
        this.queryRepository = queryRepository;
        this.scanRepository = scanRepository;
        this.scanAnalysisRepository = scanAnalysisRepository;
        this.reassignmentRepository = reassignmentRepository;
        this.catOwnershipPort = catOwnershipPort;
    }

    public Page history(UUID userId, UUID catId, String assignment, List<String> classifications,
                         Instant from, Instant to, Boolean disputed, String cursor, int limit) {
        HistoryFilter filter = new HistoryFilter(userId, catId, assignment, classifications, from, to, disputed);
        return queryRepository.findHistory(filter, cursor, Math.min(Math.max(limit, 1), 100));
    }

    public Summary summary(UUID userId, UUID catId, Instant from, Instant to) {
        return queryRepository.summarize(new HistoryFilter(userId, catId, null, null, from, to, null));
    }

    public Row detail(UUID userId, UUID scanId) {
        Row row = queryRepository.findRowByScanId(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        return requireVisible(row, userId);
    }

    public ScanAnalysis analysis(UUID userId, UUID scanId) {
        Row row = detail(userId, scanId);
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        return scanAnalysisRepository.findById(
                scan.getCurrentAnalysisId() == null ? scanId : scan.getCurrentAnalysisId())
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
    }

    public List<ScanAnalysisRecompute> recomputeHistoryPlaceholder() {
        return List.of();
    }

    public Row byRequest(UUID userId, String scanRequestId) {
        Scan scan = scanRepository.findByUserIdAndIdempotencyKey(userId, scanRequestId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_REQUEST_NOT_FOUND));
        return detail(userId, scan.getId());
    }

    public String catName(UUID catId) {
        if (catId == null) {
            return null;
        }
        return catOwnershipPort.findSnapshot(catId).map(CatOwnershipPort.CatSnapshot::name).orElse(null);
    }

    public long reassignmentCount(UUID scanId) {
        return reassignmentRepository.countByScanId(scanId);
    }

    private Row requireVisible(Row row, UUID userId) {
        if (!row.userId().equals(userId)) {
            throw new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND);
        }
        if ("INCONCLUSIVE".equals(row.classification())) {
            throw new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND);
        }
        return row;
    }

    public Optional<Row> findRow(UUID scanId) {
        return queryRepository.findRowByScanId(scanId);
    }
}
