package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;

import java.util.Optional;
import java.util.UUID;

public interface ColorChartRepository {

    Optional<ColorChart> findById(UUID id);

    Optional<ColorChart> findByCodeAndVersion(String code, int version);

    Optional<ColorChart> findActiveByProductLineAndBatch(ProductLine productLine, String productionBatch);

    boolean existsActiveByProductLineAndBatch(ProductLine productLine, String productionBatch);

    PageResult<ColorChart> findAllByStatus(ChartStatus status, int page, int size);

    PageResult<ColorChart> findAll(int page, int size);

    ColorChart save(ColorChart chart);
}
