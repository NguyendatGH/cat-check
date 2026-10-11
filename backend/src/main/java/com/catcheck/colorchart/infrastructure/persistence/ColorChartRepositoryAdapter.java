package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link ColorChartRepository} sang Spring Data (R2).
 */
@Repository
class ColorChartRepositoryAdapter implements ColorChartRepository {

    private final ColorChartJpaRepository jpaRepository;

    ColorChartRepositoryAdapter(ColorChartJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ColorChart> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ColorChart> findByCodeAndVersion(String code, int version) {
        return jpaRepository.findByCodeAndVersion(code, version);
    }

    @Override
    public Optional<ColorChart> findActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
        return jpaRepository.findTopByProductLineAndProductionBatchAndStatusOrderByVersionDesc(
                productLine, productionBatch, ChartStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveByProductLineAndBatch(ProductLine productLine, String productionBatch) {
        return findActiveByProductLineAndBatch(productLine, productionBatch).isPresent();
    }

    @Override
    public PageResult<ColorChart> findAllByStatus(ChartStatus status, int page, int size) {
        var springPage = jpaRepository.findAllByStatus(status, PageRequest.of(page, size));
        return PageResult.of(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements());
    }

    @Override
    public PageResult<ColorChart> findAll(int page, int size) {
        var springPage = jpaRepository.findAll(PageRequest.of(page, size));
        return PageResult.of(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements());
    }

    /**
     * {@code saveAndFlush} chứ không phải {@code save} trần: {@code ColorChartAdminService.publish}
     * bắt {@code DataIntegrityViolationException} của {@code uq_color_chart_active_per_batch} (V9)
     * để trả {@code 409 COLOR_CHART_IN_USE} thay vì {@code 500} khi hai admin publish gần như đồng
     * thời cho cùng (product_line, production_batch). {@code save} trần trì hoãn INSERT/UPDATE tới
     * lúc flush cuối transaction — vi phạm ràng buộc chỉ lộ ra SAU khi phương thức service đã
     * return, ngoài mọi khối {@code try/catch} ở đó, nên request thua cuộc vẫn rơi vào
     * {@code handleUnexpected()}. Cùng lý do mà {@code MonitoringRuleRepositoryAdapter.saveAndReload}
     * và {@code HealthFlagRepositoryAdapter.saveIfNotDuplicate} đã dùng {@code saveAndFlush}.
     */
    @Override
    public ColorChart save(ColorChart chart) {
        return jpaRepository.saveAndFlush(chart);
    }
}
