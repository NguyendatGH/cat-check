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

@Service
public class ChartBackfillAdminService {

    public static final Period DEFAULT_WINDOW = Period.ofDays(90);

    public static final Period MAX_WINDOW = Period.ofYears(2);

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


    @Transactional
    public void startPreview(UUID chartId, String window, AdminAction action) {
        ColorChart chart = requireBackfillable(chartId);
        Period period = parseWindow(window);
        audit("ADMIN_CHART_BACKFILL_PREVIEW", chart, action, Map.of("window", period.toString()));
        chartBackfill.startPreview(chartId, period);
    }


    @Transactional(readOnly = true)
    public ChartBackfill.BackfillImpact impact(UUID chartId) {
        requireChart(chartId);
        return chartBackfill.impact(chartId);
    }


    @Transactional
    public void startApply(UUID chartId, AdminAction action) {
        ColorChart chart = requireBackfillable(chartId);
        ChartBackfill.BackfillImpact impact = chartBackfill.impact(chartId);
        if (impact.evaluated() == 0) {
            throw new BusinessRuleException(ColorChartErrorCode.COLOR_CHART_INCOMPLETE,
                    "backfillPreview");
        }
        audit("ADMIN_CHART_BACKFILL_APPLY", chart, action, Map.of(
                "evaluated", impact.evaluated(),
                "flipped", impact.flipped()));
        chartBackfill.startApply(chartId);
    }


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
