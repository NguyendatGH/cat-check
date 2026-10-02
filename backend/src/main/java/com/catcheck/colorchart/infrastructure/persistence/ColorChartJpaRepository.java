package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ProductLine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code color_chart}.
 *
 * <p>Nằm ở {@code infrastructure.persistence} vì R7 chỉ cho phép class trong package này phụ thuộc
 * {@code org.springframework.data..}. Tầng application chỉ biết {@code ColorChartRepository} ở
 * {@code colorchart.domain.port} (R2).
 */
interface ColorChartJpaRepository extends JpaRepository<ColorChart, UUID> {

    Optional<ColorChart> findByCodeAndVersion(String code, int version);

    Optional<ColorChart> findTopByProductLineAndProductionBatchAndStatusOrderByVersionDesc(
            ProductLine productLine, String productionBatch, ChartStatus status);

    Page<ColorChart> findAllByStatus(ChartStatus status, Pageable pageable);

    Page<ColorChart> findAll(Pageable pageable);

    long countByStatus(ChartStatus status);
}
