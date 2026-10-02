package com.catcheck.colorchart.application;

import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.ChartSource;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.ColorChartPoint;
import com.catcheck.colorchart.domain.PageResult;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quản trị bảng màu — L27 đến L32 (p8 §8.4.12).
 *
 * <p>Nguyên tắc cốt lõi (p4 D5, p6 §6.6.5): <b>không bao giờ sửa bản {@code ACTIVE}</b>.
 * Publish = tạo version mới + archive bản cũ. Sửa bản {@code ACTIVE} trả
 * {@code 409 COLOR_CHART_IN_USE}.
 */
@Service
public class ColorChartAdminService {

    private final ColorChartRepository colorChartRepository;
    private final ColorChartPointRepository pointRepository;
    private final ChartPublishValidator publishValidator;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ColorChartAdminService(ColorChartRepository colorChartRepository,
                                  ColorChartPointRepository pointRepository,
                                  ChartPublishValidator publishValidator,
                                  UuidV7 uuidV7,
                                  Clock clock) {
        this.colorChartRepository = colorChartRepository;
        this.pointRepository = pointRepository;
        this.publishValidator = publishValidator;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ L27

    @Transactional(readOnly = true)
    public PageResult<ColorChart> listForAdmin(ChartStatus status, int page, int size) {
        int cappedSize = Math.min(size, 100);
        if (status != null) {
            return colorChartRepository.findAllByStatus(status, page, cappedSize);
        }
        return colorChartRepository.findAll(page, cappedSize);
    }

    // ------------------------------------------------------------------ L28

    @Transactional
    public ColorChart createDraft(String code, String name, ProductLine productLine,
                                  String productionBatch, UUID cardLayoutId, UUID createdBy) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, "code");
        }
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, "name");
        }
        int nextVersion = colorChartRepository.findByCodeAndVersion(code, 1)
                .map(c -> c.getVersion() + 1)
                .orElse(1);

        Instant now = clock.instant();
        ColorChart chart = new ColorChart(
                uuidV7.generate(),
                code,
                nextVersion,
                name,
                productLine,
                productionBatch,
                cardLayoutId,
                ChartStatus.DRAFT,
                true,
                "D65",
                "2",
                "CIEDE2000",
                Map.of("kL", 2, "kC", 1, "kH", 1, "matchScaleDE", 15),
                ChartSource.MANUAL_HEX,
                null,
                null,
                null,
                null,
                createdBy,
                now,
                now);
        return colorChartRepository.save(chart);
    }

    // ------------------------------------------------------------------ L29

    @Transactional(readOnly = true)
    public ColorChartDetail findDetail(UUID chartId) {
        ColorChart chart = colorChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundException(ColorChartErrorCode.COLOR_CHART_NOT_FOUND, chartId.toString()));
        List<ColorChartPoint> points = pointRepository.findByChartIdOrderBySortOrder(chartId);
        return new ColorChartDetail(chart, points);
    }

    // ------------------------------------------------------------------ L30

    @Transactional
    public ColorChart updateDraft(UUID chartId, String name, ProductLine productLine,
                                  String productionBatch, UUID cardLayoutId) {
        ColorChart chart = mustFind(chartId);
        if (chart.getStatus() != ChartStatus.DRAFT) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_IN_USE,
                    chartId.toString(), chart.getStatus().name());
        }
        if (name != null && !name.isBlank()) {
            chart.setName(name);
        }
        if (productLine != null) {
            chart.setProductLine(productLine);
        }
        if (productionBatch != null) {
            chart.setProductionBatch(productionBatch.isBlank() ? null : productionBatch);
        }
        if (cardLayoutId != null) {
            chart.setCardLayoutId(cardLayoutId);
        }
        chart.markUpdated(clock.instant());
        return colorChartRepository.save(chart);
    }

    // ------------------------------------------------------------------ L31

    /**
     * Thay toàn bộ danh sách mức pH (p8 L31). Admin nhập hex hoặc Lab — server tự tính Lab
     * từ hex khi {@code source = MANUAL_HEX} (p6 §6.6.3 luồng 1).
     *
     * <p>Xoá toàn bộ điểm cũ rồi ghi mới trong <b>cùng transaction</b> — không có cửa sổ nào
     * mà bảng không có điểm.
     */
    @Transactional
    public ColorChartDetail replacePoints(UUID chartId, List<PointInput> inputs) {
        ColorChart chart = mustFind(chartId);
        if (chart.getStatus() != ChartStatus.DRAFT) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_IN_USE,
                    chartId.toString(), chart.getStatus().name());
        }
        if (inputs == null || inputs.isEmpty()) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_INCOMPLETE, "points");
        }

        pointRepository.deleteByChartId(chartId);

        Instant now = clock.instant();
        List<ColorChartPoint> saved = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            PointInput input = inputs.get(i);
            ColorChartPoint point = toPoint(chartId, input, i + 1, now);
            saved.add(pointRepository.save(point));
        }

        chart.markUpdated(now);
        colorChartRepository.save(chart);
        return new ColorChartDetail(chart, saved);
    }

    // ------------------------------------------------------------------ L32

    /**
     * Publish: {@code DRAFT} ⇒ {@code ACTIVE}, bản cũ ⇒ {@code ARCHIVED} (p8 L32).
     *
     * <p>Validate bốn điều kiện p6 §6.6.2 trước khi cho publish. Thiếu điểm/dải ⇒
     * {@code 422 COLOR_CHART_INCOMPLETE}. Trùng {@code (product_line, production_batch)} với bản
     * {@code ACTIVE} khác ⇒ {@code 409 COLOR_CHART_IN_USE}.
     */
    @Transactional
    public ColorChart publish(UUID chartId) {
        ColorChart chart = mustFind(chartId);
        if (chart.getStatus() != ChartStatus.DRAFT) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_IN_USE,
                    chartId.toString(), chart.getStatus().name());
        }

        List<ColorChartPoint> points = pointRepository.findByChartIdOrderBySortOrder(chartId);
        ChartPublishValidator.Result validation = publishValidator.validate(chart, points);
        if (!validation.valid()) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_INCOMPLETE,
                    String.join(",", validation.violations()));
        }

        if (chart.getProductLine() != null
                && colorChartRepository.existsActiveByProductLineAndBatch(
                        chart.getProductLine(), chart.getProductionBatch())) {
            throw new ConflictException(ColorChartErrorCode.COLOR_CHART_IN_USE,
                    chartId.toString(), "ACTIVE");
        }

        Instant now = clock.instant();

        // Archive bản ACTIVE cũ của cùng (product_line, production_batch).
        colorChartRepository.findActiveByProductLineAndBatch(
                        chart.getProductLine(), chart.getProductionBatch())
                .ifPresent(old -> {
                    old.setStatus(ChartStatus.ARCHIVED);
                    old.setEffectiveTo(now);
                    old.markUpdated(now);
                    colorChartRepository.save(old);
                });

        chart.setStatus(ChartStatus.ACTIVE);
        chart.setEffectiveFrom(now);
        chart.setPublishedAt(now);
        chart.markUpdated(now);
        return colorChartRepository.save(chart);
    }

    // ------------------------------------------------------------------ nội bộ

    private ColorChart mustFind(UUID chartId) {
        return colorChartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundException(ColorChartErrorCode.COLOR_CHART_NOT_FOUND, chartId.toString()));
    }

    private ColorChartPoint toPoint(UUID chartId, PointInput input, int sortOrder, Instant now) {
        return new ColorChartPoint(
                uuidV7.generate(),
                chartId,
                input.phValue(),
                sortOrder,
                input.labL(),
                input.labA(),
                input.labB(),
                input.toleranceDeltaE(),
                input.hexSrgb(),
                input.displayHex(),
                input.displayNameVi(),
                input.displayNameEn(),
                null,
                null,
                now,
                now);
    }

    /**
     * Kết quả L29 — chi tiết bảng màu + toàn bộ điểm.
     */
    public record ColorChartDetail(ColorChart chart, List<ColorChartPoint> points) {
    }

    /**
     * Đầu vào một mức pH (L31). Lab có thể nhập trực tiếp hoặc suy ra từ hex — xem
     * {@code ColorChartPointRequest} ở tầng API.
     */
    public record PointInput(
            java.math.BigDecimal phValue,
            java.math.BigDecimal labL,
            java.math.BigDecimal labA,
            java.math.BigDecimal labB,
            java.math.BigDecimal toleranceDeltaE,
            String hexSrgb,
            String displayHex,
            String displayNameVi,
            String displayNameEn) {
    }
}
