package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanAnalysis;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanAnalysisRepository {

    ScanAnalysis save(ScanAnalysis analysis);

    Optional<ScanAnalysis> findCurrentByScanId(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    Optional<ScanAnalysis> findById(UUID id);

    List<ScanAnalysis> findBackfillCandidates(UUID chartId, Instant computedFrom, String engineVersionPrefix,
                                              int limit, int offset);

    long countBackfillCandidates(UUID chartId, Instant computedFrom, String engineVersionPrefix);
}
