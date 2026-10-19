package com.catcheck.scan.domain.color;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartCatalog {

    Optional<PhChart> findActiveChart(String productLine, String productionBatch);

    Optional<PhChart> findChartById(UUID chartId);

    List<PhBandClassifier.Band> findGlobalBands();
}
