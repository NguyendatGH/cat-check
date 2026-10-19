package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.ScanRecomputeImpact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanAnalysisRecomputeRepository {

    void saveAll(List<ScanAnalysisRecompute> rows);

    int deleteByChartId(UUID chartId);

    ScanRecomputeImpact impactByChartId(UUID chartId);

    List<ScanAnalysisRecompute> findTopByChartIdOrderByAbsDeltaPhDesc(UUID chartId, int limit);

    List<ScanAnalysisRecompute> findByChartId(UUID chartId);

    Optional<UUID> findLatestJobIdByChartId(UUID chartId);
}
