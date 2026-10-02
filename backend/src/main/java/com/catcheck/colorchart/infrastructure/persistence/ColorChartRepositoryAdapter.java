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

    @Override
    public ColorChart save(ColorChart chart) {
        return jpaRepository.save(chart);
    }
}
