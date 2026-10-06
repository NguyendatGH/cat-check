package com.catcheck.colorchart.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.colorchart.api.ColorChartErrorCode;
import com.catcheck.colorchart.domain.ChartStatus;
import com.catcheck.colorchart.domain.ColorChart;
import com.catcheck.colorchart.domain.port.ColorChartPointRepository;
import com.catcheck.colorchart.domain.port.ColorChartRepository;
import com.catcheck.scan.api.ChartBackfill;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Period;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;

/**
 * L33, L34, L35 — backfill bảng màu phía quản trị (p8 §8.4.12 mục (c)).
 *
 * <p>Việc của lớp này là <b>luật nghiệp vụ quanh backfill</b>, không phải phép tính: kiểm bảng
 * màu có tồn tại và có đủ điểm để tính, chặn backfill trên bảng {@code ARCHIVED}, parse cửa sổ
 * thời gian, và ghi {@code audit_log} trong cùng transaction (ký hiệu {@code Aud}). Phép tính
 * S8→S10 nằm sau cổng {@link ChartBackfill} của module {@code scan} — xem javadoc cổng đó về lý
 * do.</p>
 */
@Service
public class ChartBackfillAdminService {

    /** Cửa sổ mặc định khi admin không chọn — p6 §6.5.4 lấy {@code P90D} làm ví dụ chuẩn. */
    public static final Period DEFAULT_WINDOW = Period.ofDays(90);

    /**
     * Trần cửa sổ: 2 năm. Không phải con số của spec — p6 §6.5.4 không đặt trần — mà là lưới an
     * toàn vận hành: {@code P100Y} sẽ quét toàn bộ {@code scan_analysis} trong một transaction.
     * Ghi handoff H15.162.
     */
    public static final Period MAX_WINDOW = Period.ofYears(2);

    /** Bảng màu phải có ít nhất 2 mức để nội suy được pH (bất biến của {@code ChartMatcher}). */
    private static final int MIN_POINTS = 2;

    private final ColorChartRepository chartRepository;
    private final ColorChartPointRepository pointRepository;
    private final ChartBackfill chartBackfill;
    private final AuditLogService auditLog;

    public ChartBackfillAdminService(ColorChartRepository chartRepository,
                                     ColorChartPointRepository pointRepository,
                                     ChartBackfill chartBackfill,
                                     AuditLogService auditLog) {
        this.chartRepository = chartRepository;
        this.pointRepository = pointRepository;
        this.chartBackfill = chartBackfill;
        this.auditLog = auditLog;
    }

    // ------------------------------------------------------------------ L33

    @Transactional
    public void startPreview(UUID chartId, String window, AdminAction action) {
        ColorChart chart = requireBackfillable(chartId);
        Period period = parseWindow(window);
        audit("ADMIN_CHART_BACKFILL_PREVIEW", chart, action, Map.of("window", period.toString()));
        chartBackfill.startPreview(chartId, period);
    }

    // ------------------------------------------------------------------ L34

    @Transactional(readOnly = true)
    public ChartBackfill.BackfillImpact impact(UUID chartId) {
        requireChart(chartId);
        return chartBackfill.impact(chartId);
    }

    // ------------------------------------------------------------------ L35

    @Transactional
    public void startApply(UUID chartId, AdminAction action) {
        ColorChart chart = requireBackfillable(chartId);
        ChartBackfill.BackfillImpact impact = chartBackfill.impact(chartId);
        if (impact.evaluated() == 0) {
            // Áp dụng một preview chưa chạy sẽ "thành công" mà không đổi dòng nào — im lặng
            // đúng kiểu khiến admin tưởng đã backfill. 422 vì cú pháp đúng, trạng thái sai.
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_INCOMPLETE,
                    "backfillPreview");
        }
        audit("ADMIN_CHART_BACKFILL_APPLY", chart, action, Map.of(
                "evaluated", impact.evaluated(),
                "flipped", impact.flipped()));
        chartBackfill.startApply(chartId);
    }

    // ------------------------------------------------------------------ nội bộ

    /**
     * Cửa sổ thời gian dạng ISO-8601 ({@code P90D}, {@code P6M}).
     *
     * <p>{@code Period.parse} ném {@code DateTimeParseException} (một {@code RuntimeException}
     * không ai bắt) nên phải chuyển thành {@code 400} ở đây; để nó lọt lên là một {@code 500}
     * cho một lỗi nhập liệu.</p>
     */
    public static Period parseWindow(String window) {
        if (window == null || window.isBlank()) {
            return DEFAULT_WINDOW;
        }
        Period period;
        try {
            period = Period.parse(window.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, "window");
        }
        if (period.isNegative() || period.isZero()) {
            throw new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, "window");
        }
        if (toApproxDays(period) > toApproxDays(MAX_WINDOW)) {
            throw new BusinessRuleException(ColorChartErrorCode.VALIDATION_FAILED, "window");
        }
        return period;
    }

    /** So sánh hai {@link Period} cần quy về một đơn vị; 30/365 là xấp xỉ, đủ cho một trần an toàn. */
    private static long toApproxDays(Period period) {
        return period.toTotalMonths() * 30L + period.getDays();
    }

    private ColorChart requireChart(UUID chartId) {
        return chartRepository.findById(chartId)
                .orElseThrow(() -> new NotFoundException(
                        ColorChartErrorCode.COLOR_CHART_NOT_FOUND, chartId.toString()));
    }

    private ColorChart requireBackfillable(UUID chartId) {
        ColorChart chart = requireChart(chartId);
        if (chart.getStatus() == ChartStatus.ARCHIVED) {
            // Tính lại kết quả cũ theo một bảng đã bị thay thế là đi lùi — 409 vì đây là xung
            // đột trạng thái, cùng mã mà L30 dùng cho "bảng đang được dùng".
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_IN_USE,
                    chartId.toString(), chart.getStatus().name());
        }
        if (pointRepository.findByChartIdOrderBySortOrder(chartId).size() < MIN_POINTS) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_INCOMPLETE, "points");
        }
        return chart;
    }

    private void audit(String action, ColorChart chart, AdminAction adminAction, Map<String, Object> extra) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>(extra);
        metadata.put("chartId", chart.getId().toString());
        metadata.put("chartCode", chart.getCode());
        metadata.put("chartVersion", chart.getVersion());
        metadata.put("reason", adminAction.reason());
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminAction.actorId(), adminAction.actorRole()))
                .subject(AuditSubjectType.SETTING, null)
                .action(action)
                .metadata(metadata)
                .requestId(adminAction.requestId())
                .ipAddress(adminAction.ipAddress())
                .userAgent(adminAction.userAgent())
                .build());
    }
}
