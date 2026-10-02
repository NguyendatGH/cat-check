package com.catcheck.scan.application;

import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.scan.domain.port.ScanQueryRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hiện thực {@link ScanHistoryQuery} (cổng do {@code scan} công bố cho {@code insight}). */
@Component
public class ScanHistoryQueryAdapter implements ScanHistoryQuery {

    private final ScanQueryRepository queryRepository;

    public ScanHistoryQueryAdapter(ScanQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    @Override
    public List<RecentScan> recentValidScans(UUID catId, Instant since, int limit) {
        return queryRepository.findRecentForRules(catId, since, limit).stream()
                .map(row -> new RecentScan(row.scanId(), row.capturedAt(), row.classification(),
                        row.phValue(), row.confidence(), row.nearBoundary(), row.calibrationMethod()))
                .toList();
    }

    @Override
    public List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to) {
        return queryRepository.findForExport(catId, from, to).stream()
                .map(row -> new ExportScanRow(
                        row.scanId(), row.capturedAt(), row.phValue(), row.classification(),
                        row.confidence(), row.nearBoundary(), row.disputedAt() != null,
                        row.calibrationMethod(), flagCodes(row), row.imageStored()))
                .toList();
    }

    private java.util.List<String> flagCodes(com.catcheck.scan.domain.port.ScanQueryRepository.Row row) {
        if (row.qualityFlags() == null) {
            return java.util.List.of();
        }
        return row.qualityFlags().stream()
                .map(m -> String.valueOf(m.get("code")))
                .toList();
    }
}
