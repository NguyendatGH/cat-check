package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysisRecompute;
import com.catcheck.scan.domain.ScanRecomputeImpact;
import com.catcheck.scan.domain.port.ScanAnalysisRecomputeRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ScanAnalysisRecomputeRepositoryAdapter implements ScanAnalysisRecomputeRepository {

    private final ScanAnalysisRecomputeJpaRepository jpaRepository;

    ScanAnalysisRecomputeRepositoryAdapter(ScanAnalysisRecomputeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void saveAll(List<ScanAnalysisRecompute> rows) {
        jpaRepository.saveAll(rows);
    }

    @Override
    public int deleteByChartId(UUID chartId) {
        return jpaRepository.deleteByChartId(chartId);
    }

    @Override
    public ScanRecomputeImpact impactByChartId(UUID chartId) {
        Object[] row = jpaRepository.aggregateByChartId(chartId).stream().findFirst().orElse(null);
        // Hibernate trả về Object[]{Object[]{...}} cho projection nhiều cột khi method khai
        // Object[] — mở một lớp nếu gặp dạng đó, thay vì ép kiểu và nổ ClassCastException.
        Object[] values = row != null && row.length == 1 && row[0] instanceof Object[] nested ? nested : row;
        if (values == null || values.length < 3) {
            return ScanRecomputeImpact.empty();
        }
        long evaluated = values[0] instanceof Number n ? n.longValue() : 0L;
        long flipped = values[1] instanceof Number n ? n.longValue() : 0L;
        BigDecimal maxAbsDeltaPh = values[2] instanceof BigDecimal b ? b
                : values[2] instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : null;
        return new ScanRecomputeImpact(evaluated, flipped, maxAbsDeltaPh,
                findLatestJobIdByChartId(chartId).orElse(null));
    }

    @Override
    public List<ScanAnalysisRecompute> findTopByChartIdOrderByAbsDeltaPhDesc(UUID chartId, int limit) {
        return jpaRepository.findTopByChartId(chartId, PageRequest.of(0, Math.max(1, limit)));
    }

    @Override
    public List<ScanAnalysisRecompute> findByChartId(UUID chartId) {
        return jpaRepository.findByChartId(chartId);
    }

    @Override
    public Optional<UUID> findLatestJobIdByChartId(UUID chartId) {
        return jpaRepository.findJobIds(chartId, PageRequest.of(0, 1)).stream().findFirst();
    }
}
