package com.catcheck.colorchart.application;

import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.scan.domain.color.ChartCatalog;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hiện thực {@link ChartCatalog} (cổng do scan tuyên bố) bằng dữ liệu của module {@code colorchart}.
 *
 * <p>Đây là điểm nối duy nhất giữa hai module: scan không import bất kỳ class nào của
 * colorchart, chỉ biết {@code ChartCatalog}. Chiều phụ thuộc compile-time là
 * colorchart → scan (xem {@code package-info} của colorchart).
 *
 * <p>Map entity → mô hình nội bộ của scan ({@link PhChart}, {@link PhBandClassifier.Band}) tại
 * đây để A6 không phải biết về entity của colorchart.
 */
@Component
public class ColorChartCatalogAdapter implements ChartCatalog {

    private final ColorChartRepository colorChartRepository;
    private final ColorChartPointRepository pointRepository;
    private final PhClassificationBandRepository bandRepository;
    private final ChartPublishValidator publishValidator;

    public ColorChartCatalogAdapter(ColorChartRepository colorChartRepository,
                                    ColorChartPointRepository pointRepository,
                                    PhClassificationBandRepository bandRepository,
                                    ChartPublishValidator publishValidator) {
        this.colorChartRepository = colorChartRepository;
        this.pointRepository = pointRepository;
        this.bandRepository = bandRepository;
        this.publishValidator = publishValidator;
    }

    @Override
    public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
        ProductLine line = ProductLine.valueOf(productLine);
        return colorChartRepository
                .findActiveByProductLineAndBatch(line, productionBatch)
                .map(this::toPhChart);
    }

    /** Backfill L33–L35: bảng màu theo id, không lọc trạng thái (xem javadoc của cổng). */
    @Override
    public Optional<PhChart> findChartById(UUID chartId) {
        return colorChartRepository.findById(chartId).map(this::toPhChart);
    }

    @Override
    public List<PhBandClassifier.Band> findGlobalBands() {
        return bandRepository.findGlobalActiveOrderBySortOrder().stream()
                .map(this::toBand)
                .toList();
    }

    private PhChart toPhChart(ColorChart chart) {
        List<PhChartPoint> points = pointRepository.findByChartIdOrderBySortOrder(chart.getId()).stream()
                .map(this::toPhChartPoint)
                .toList();
        DeltaE2000Params params = publishValidator.parseParams(chart);
        return new PhChart(
                chart.getId().toString(),
                chart.getVersion(),
                PhChart.Status.valueOf(chart.getStatus().name()),
                chart.getIlluminant(),
                Integer.parseInt(chart.getObserver()),
                chart.isPlaceholder(),
                points,
                params);
    }

    private PhChartPoint toPhChartPoint(ColorChartPoint point) {
        return new PhChartPoint(
                point.getId().toString(),
                point.getPhValue().doubleValue(),
                new Lab(point.getLabL().doubleValue(), point.getLabA().doubleValue(), point.getLabB().doubleValue()),
                point.getToleranceDeltaE().doubleValue(),
                point.getHexSrgb(),
                point.getDisplayHex(),
                point.getDisplayNameVi(),
                point.getDisplayNameEn(),
                point.getSampleCount() != null ? point.getSampleCount() : 0,
                point.getSampleSpreadDe00() != null ? point.getSampleSpreadDe00().doubleValue() : 0.0);
    }

    private PhBandClassifier.Band toBand(PhClassificationBand band) {
        return new PhBandClassifier.Band(
                PhBandClassifier.Code.valueOf(band.getCode()),
                band.getMinPh() != null ? band.getMinPh().doubleValue() : null,
                band.getMaxPh() != null ? band.getMaxPh().doubleValue() : null,
                band.isMinInclusive(),
                band.isMaxInclusive(),
                band.getCode(),
                PhBandClassifier.Severity.valueOf(band.getSeverity().name()),
                band.getColorToken(),
                band.getIconName(),
                band.getSortOrder());
    }
}
