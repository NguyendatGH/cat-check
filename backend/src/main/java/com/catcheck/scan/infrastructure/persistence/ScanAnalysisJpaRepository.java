package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysis;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScanAnalysisJpaRepository extends JpaRepository<ScanAnalysis, UUID> {

    String BACKFILL_CANDIDATE_FILTER = """
             FROM scan_analysis a
             JOIN scan s ON s.id = a.scan_id
            WHERE a.is_current
              AND a.lab_l IS NOT NULL AND a.lab_a IS NOT NULL AND a.lab_b IS NOT NULL
              AND a.computed_at >= :computedFrom
              AND a.engine_version LIKE CONCAT(:engineVersionPrefix, '%')
              AND s.chart_id IN (SELECT c.id
                                   FROM color_chart c
                                   JOIN color_chart target ON target.id = :chartId
                                  WHERE c.product_line IS NOT DISTINCT FROM target.product_line
                                    AND c.production_batch IS NOT DISTINCT FROM target.production_batch)
            """;

    Optional<ScanAnalysis> findByScanIdAndCurrentTrue(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);

    @Query(value = "SELECT a.*" + BACKFILL_CANDIDATE_FILTER + " ORDER BY a.computed_at ASC, a.id ASC",
            nativeQuery = true)
    List<ScanAnalysis> findBackfillCandidates(@Param("chartId") UUID chartId,
                                              @Param("computedFrom") Instant computedFrom,
                                              @Param("engineVersionPrefix") String engineVersionPrefix,
                                              Pageable pageable);

    @Query(value = "SELECT count(*)" + BACKFILL_CANDIDATE_FILTER, nativeQuery = true)
    long countBackfillCandidates(@Param("chartId") UUID chartId,
                                 @Param("computedFrom") Instant computedFrom,
                                 @Param("engineVersionPrefix") String engineVersionPrefix);
}
