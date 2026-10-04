package com.catcheck.scan.domain.color;

import java.util.List;
import java.util.Optional;

/**
 * Port used by scan to read the active pH chart and global classification bands.
 * The colorchart module supplies the implementation.
 */
public interface ChartCatalog {

    Optional<PhChart> findActiveChart(String productLine, String productionBatch);

    List<PhBandClassifier.Band> findGlobalBands();
}
