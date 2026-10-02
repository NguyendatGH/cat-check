package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Truy vấn công khai — phục vụ F1 ({@code GET /reference/ph-bands}) và F5
 * ({@code GET /reference/color-charts/active}).
 *
 * <p>F5 <b>không</b> trả toạ độ Lab của từng điểm — đó là tài sản hiệu chuẩn (p8 §8.4.6).
 * Pipeline scan lấy Lab qua {@code ChartCatalog} (cổng nội bộ), không qua API công khai.
 */
@Service
public class ColorChartQueryService {

    private final ColorChartRepository colorChartRepository;
    private final PhClassificationBandRepository bandRepository;

    public ColorChartQueryService(ColorChartRepository colorChartRepository,
                                  PhClassificationBandRepository bandRepository) {
        this.colorChartRepository = colorChartRepository;
        this.bandRepository = bandRepository;
    }

    /**
     * Bảng màu đang {@code ACTIVE} cho một dòng sản phẩm. Ưu tiên bản gắn đúng lô, không có thì
     * bản mặc định của dòng (p6 §6.6.4).
     */
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

    /** 6 dải phân loại toàn cục — nguồn duy nhất cho {@code PhGaugeBar}/{@code PhBadge} (F1). */
    public List<PhClassificationBand> findGlobalBands() {
        return bandRepository.findGlobalActiveOrderBySortOrder();
    }
}
