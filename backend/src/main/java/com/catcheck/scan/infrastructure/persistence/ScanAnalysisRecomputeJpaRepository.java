package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysisRecompute;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface ScanAnalysisRecomputeJpaRepository extends JpaRepository<ScanAnalysisRecompute, UUID> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM ScanAnalysisRecompute r WHERE r.chartId = :chartId")
    int deleteByChartId(@Param("chartId") UUID chartId);

    /**
     * Ba con số của L34 trong một lượt đi DB: đã xét, bị lật phân loại, {@code |deltaPh|} lớn
     * nhất. Nạp hết dòng về rồi đếm trong Java là cách nhanh nhất biến một màn admin thành một
     * lần OOM — cửa sổ mặc định P90D trên hệ thống thật là hàng chục nghìn dòng.
     */
    @Query("SELECT count(r), sum(CASE WHEN r.flippedClassification = true THEN 1 ELSE 0 END),"
            + " max(abs(r.deltaPh))"
            + " FROM ScanAnalysisRecompute r WHERE r.chartId = :chartId")
    List<Object[]> aggregateByChartId(@Param("chartId") UUID chartId);

    @Query("SELECT r FROM ScanAnalysisRecompute r WHERE r.chartId = :chartId"
            + " ORDER BY abs(r.deltaPh) DESC NULLS LAST, r.id ASC")
    List<ScanAnalysisRecompute> findTopByChartId(@Param("chartId") UUID chartId, Pageable pageable);

    List<ScanAnalysisRecompute> findByChartId(UUID chartId);

    @Query("SELECT r.jobId FROM ScanAnalysisRecompute r"
            + " WHERE r.chartId = :chartId AND r.jobId IS NOT NULL"
            + " ORDER BY r.computedAt DESC")
    List<UUID> findJobIds(@Param("chartId") UUID chartId, Pageable pageable);
}
