package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ColorChartQueryService {

    private final ColorChartRepository colorChartRepository;
    private final PhClassificationBandRepository bandRepository;

    public ColorChartQueryService(ColorChartRepository colorChartRepository,
                                  PhClassificationBandRepository bandRepository) {
        this.colorChartRepository = colorChartRepository;
        this.bandRepository = bandRepository;
    }

    public ColorChart findActiveChart(ProductLine productLine, String productionBatch) {
        if (productionBatch != null && !productionBatch.isBlank()) {
            var byBatch = colorChartRepository.findActiveByProductLineAndBatch(productLine, productionBatch);
            if (byBatch.isPresent()) {
                return byBatch.get();
            }
        }
        return colorChartRepository.findActiveByProductLineAndBatch(productLine, null)
                .orElseThrow(() -> new ColorChartUnavailableException(productLine, productionBatch));
    }

    public List<PhClassificationBand> findGlobalBands() {
        return bandRepository.findGlobalActiveOrderBySortOrder();
    }
}
