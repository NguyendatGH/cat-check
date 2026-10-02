package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanAnalysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cổng đọc/ghi {@code scan_analysis} (p4 D3). */
public interface ScanAnalysisRepository {

    ScanAnalysis save(ScanAnalysis analysis);

    Optional<ScanAnalysis> findCurrentByScanId(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    Optional<ScanAnalysis> findById(UUID id);
}
