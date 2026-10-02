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

/**
 * Truy vấn hiển thị — {@code GET /scans}, {@code /scans/summary}, {@code /scans/{id}},
 * {@code /scans/{id}/analysis}, {@code /scans/by-request/{scanRequestId}} (p8 §8.4.5/§8.5.4).
 *
 * <p>{@code INCONCLUSIVE} không có bản ghi hiển thị được: mọi phương thức đọc theo {@code scanId}
 * ở đây loại các dòng đó ra bằng {@link #requireVisible} — đúng hợp đồng p8 §8.5.4 "scanId là
 * null khi và chỉ khi không có bản ghi mà {@code GET /scans/{id}} đọc được", ngay cả khi p4 D1
 * vẫn yêu cầu lưu {@code scan}+{@code scan_analysis} cho INCONCLUSIVE để giữ lịch sử/audit.</p>
 */
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

    /** {@code GET /scans/{id}} — chỉ chủ sở hữu, chỉ khi có bản ghi hiển thị được. */
    public Row detail(UUID userId, UUID scanId) {
        Row row = queryRepository.findRowByScanId(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        return requireVisible(row, userId);
    }

    /** {@code GET /scans/{id}/analysis} — chi tiết kỹ thuật (Lab, ΔE, calibration...). */
    public ScanAnalysis analysis(UUID userId, UUID scanId) {
        Row row = detail(userId, scanId);
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        return scanAnalysisRepository.findById(
                scan.getCurrentAnalysisId() == null ? scanId : scan.getCurrentAnalysisId())
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_ANALYSIS_NOT_FOUND));
    }

    public List<ScanAnalysisRecompute> recomputeHistoryPlaceholder() {
        // M7 (backfill) ngoài phạm vi MVP — xem docs/handovers/A6.md. Giữ chỗ để không lộ
        // signature giả khi W3/M7 nối tiếp.
        return List.of();
    }

    /** {@code GET /scans/by-request/{scanRequestId}} — poll sau 409 SCAN_IN_PROGRESS (p6 §6.4.5). */
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
