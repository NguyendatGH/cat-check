package com.catcheck.insight.application;

import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.scan.api.ScanHistoryQuery;
import com.catcheck.scan.api.ScanSavedEvent;
import com.catcheck.shared.id.UuidV7;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Đánh giá rule R1-R4 (p6 §6.9) NGAY sau khi một scan hợp lệ được lưu — {@code @EventListener}
 * THƯỜNG (không {@code @Async}, không {@code @TransactionalEventListener}) nên chạy ĐỒNG BỘ,
 * CÙNG transaction với {@code ScanPersistenceService} (p7 §7.4.5): nếu ghi health_flag lỗi, cả
 * giao dịch (kể cả scan vừa lưu) rollback cùng nhau.
 *
 * <p><b>Không đánh giá {@code URGENT_CLINICAL_SIGN} ở đây</b> — rule đó không dựa trên pipeline
 * màu mà dựa trên {@code cat_clinical_sign_report} do chủ nuôi tự khai (p6 §6.9.6), một luồng
 * nhập liệu thuộc "Nhóm D — Hồ sơ mèo" (module {@code cat}, ngoài phạm vi sở hữu file của A6).
 * Đã expose {@link ClinicalSignEscalation} để module đó gọi khi có endpoint thật — xem
 * {@code docs/handovers/A6.md}.</p>
 */
@Service
public class RuleEvaluationService {

    private final MonitoringRuleRepository ruleRepository;
    private final HealthFlagRepository healthFlagRepository;
    private final ScanHistoryQuery scanHistoryQuery;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public RuleEvaluationService(MonitoringRuleRepository ruleRepository, HealthFlagRepository healthFlagRepository,
                                  ScanHistoryQuery scanHistoryQuery, UuidV7 uuidV7, Clock clock) {
        this.ruleRepository = ruleRepository;
        this.healthFlagRepository = healthFlagRepository;
        this.scanHistoryQuery = scanHistoryQuery;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @EventListener
    public void onScanSaved(ScanSavedEvent event) {
        // p6 §6.9.1 nguyên tắc 2: chỉ scan ASSIGNED mới vào baseline/rule của một mèo cụ thể.
        if (event.catId() == null || !"ASSIGNED".equals(event.assignment())) {
            return;
        }
        Instant now = clock.instant();

        evaluateLowQualityStreak(event, now);
        if (event.chartIsPlaceholder()) {
            // p6 §6.9.1 nguyên tắc 5: R1-R3 tắt khi bảng màu đang placeholder.
            return;
        }
        evaluateRepeatedOutOfRange(event, now);
        evaluateBaselineDeviation(event, now);
        evaluateMonotonicTrend(event, now);
    }

    // ------------------------------------------------------------------ R1

    private void evaluateRepeatedOutOfRange(ScanSavedEvent event, Instant now) {
        MonitoringRule rule = enabledRule(MonitoringRuleCode.REPEATED_OUT_OF_RANGE.name());
        if (rule == null) {
            return;
        }
        int windowHours = rule.paramAsInt("windowHours", 48);
        int minCount = rule.paramAsInt("minCount", 2);
        double minConfidence = rule.paramAsDouble("minConfidence", 0.55);

        Instant since = event.capturedAt().minusSeconds(windowHours * 3600L);
        List<ScanHistoryQuery.RecentScan> scans = validScans(event.catId(), since, minConfidence);

        long lowCount = scans.stream().filter(s -> isLowSide(s.classification())).count();
        long highCount = scans.stream().filter(s -> isHighSide(s.classification())).count();

        String direction = null;
        long matched = 0;
        if (lowCount >= minCount) {
            direction = "axit";
            matched = lowCount;
        } else if (highCount >= minCount) {
            direction = "kiềm";
            matched = highCount;
        }
        if (direction == null) {
            return;
        }

        Instant windowFrom = scans.stream().map(ScanHistoryQuery.RecentScan::capturedAt)
                .min(Comparator.naturalOrder()).orElse(since);
        String explanation = ("Ghi nhận lặp lại — Trong %d giờ qua, bé mèo này có %d lần đo nằm ngoài "
                + "khoảng tham chiếu 6,3–6,6, và các lần đó đều thiên về %s. Một lần lệch thường không "
                + "nói lên điều gì, nhưng lặp lại thì đáng để bạn chú ý hơn. Bạn có thể kiểm tra xem bé "
                + "có uống đủ nước không, gần đây có đổi thức ăn không, và tiếp tục quét thêm 1-2 lần "
                + "trong ngày mai. Đây là quan sát từ màu cát, không phải chẩn đoán. Nếu bé có dấu hiệu "
                + "rặn tiểu khó, bỏ ăn hoặc kêu đau khi đi vệ sinh, hãy đưa bé đến bác sĩ thú y.")
                .formatted(windowHours, matched, direction);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", matched);
        params.put("direction", direction);
        params.put("windowHours", windowHours);

        raise(rule, event, windowFrom, now, explanation, params);
    }

    private boolean isLowSide(String classification) {
        return "SLIGHTLY_LOW".equals(classification) || "LOW".equals(classification);
    }

    private boolean isHighSide(String classification) {
        return "SLIGHTLY_HIGH".equals(classification) || "HIGH".equals(classification);
    }

    // ------------------------------------------------------------------ R2

    private void evaluateBaselineDeviation(ScanSavedEvent event, Instant now) {
        MonitoringRule rule = enabledRule(MonitoringRuleCode.BASELINE_DEVIATION.name());
        if (rule == null || event.phValue() == null) {
            return;
        }
        int baselineDays = rule.paramAsInt("baselineDays", 14);
        double minDelta = rule.paramAsDouble("minDelta", 0.5);
        int minSamples = rule.paramAsInt("minBaselineSamples", 5);
        double minConfidence = rule.paramAsDouble("minConfidence", 0.55);

        Instant since = event.capturedAt().minusSeconds(baselineDays * 24 * 3600L);
        // p6 §6.9.3: chỉ scan CARD_CCM mới đủ tin cậy làm baseline — nhánh SUBSTRATE_WB/NONE của
        // MVP (xem docs/handovers/A6.md) khiến rule này gần như không bao giờ có đủ mẫu, đúng như
        // spec dự liệu ("loại scan không hiệu chỉnh tốt khỏi baseline", nguyên tắc 3).
        List<BigDecimal> baseline = validScans(event.catId(), since, minConfidence).stream()
                .filter(s -> "CARD_CCM".equals(s.calibrationMethod()))
                .map(ScanHistoryQuery.RecentScan::phValue)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (baseline.size() < minSamples) {
            return;
        }
        BigDecimal median = median(baseline);
        BigDecimal delta = event.phValue().subtract(median).abs();
        if (delta.compareTo(BigDecimal.valueOf(minDelta)) < 0) {
            return;
        }

        String explanation = ("Khác với thường ngày — Chỉ số hôm nay của bé mèo này là %s, lệch %s so với "
                + "mức thường thấy của bé trong %d ngày qua (%s). Mỗi bé mèo có mức nền riêng, nên thay đổi "
                + "so với chính bé thường đáng chú ý hơn là so với mức trung bình chung. Bạn có thể ghi lại "
                + "xem gần đây có đổi thức ăn, đổi loại cát, chuyển nhà hay có điều gì làm bé căng thẳng "
                + "không; quét lại sau 12-24 giờ để xem chỉ số có quay về mức cũ. Đây là quan sát từ màu "
                + "cát, không phải chẩn đoán.")
                .formatted(event.phValue(), delta.setScale(2, RoundingMode.HALF_UP), baselineDays, median);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ph", event.phValue());
        params.put("baseline", median);
        params.put("delta", delta);

        raise(rule, event, since, now, explanation, params);
    }

    // ------------------------------------------------------------------ R3

    private void evaluateMonotonicTrend(ScanSavedEvent event, Instant now) {
        MonitoringRule rule = enabledRule(MonitoringRuleCode.MONOTONIC_TREND.name());
        if (rule == null) {
            return;
        }
        int streak = rule.paramAsInt("streak", 3);
        int maxSpanDays = rule.paramAsInt("maxSpanDays", 7);
        int minGapHours = rule.paramAsInt("minGapHours", 6);
        double minStepDelta = rule.paramAsDouble("minStepDelta", 0.15);
        double midpoint = rule.paramAsDouble("midpoint", 6.45);
        double minConfidence = rule.paramAsDouble("minConfidence", 0.55);

        Instant since = event.capturedAt().minusSeconds(maxSpanDays * 24 * 3600L);
        List<ScanHistoryQuery.RecentScan> scans = validScans(event.catId(), since, minConfidence);
        if (scans.size() < streak) {
            return;
        }
        // scans đã sắp captured_at DESC (mới nhất trước) - lấy `streak` gần nhất rồi đảo lại thành
        // thứ tự thời gian tăng dần để kiểm đơn điệu.
        List<ScanHistoryQuery.RecentScan> latest = new ArrayList<>(scans.subList(0, streak));
        java.util.Collections.reverse(latest);

        for (int i = 1; i < latest.size(); i++) {
            long gapHours = java.time.Duration.between(latest.get(i - 1).capturedAt(), latest.get(i).capturedAt()).toHours();
            if (gapHours < minGapHours) {
                return;
            }
        }

        boolean increasing = true;
        boolean decreasing = true;
        for (int i = 1; i < latest.size(); i++) {
            BigDecimal prev = latest.get(i - 1).phValue();
            BigDecimal curr = latest.get(i).phValue();
            if (prev == null || curr == null) {
                return;
            }
            double step = curr.doubleValue() - prev.doubleValue();
            if (step < minStepDelta) {
                increasing = false;
            }
            if (-step < minStepDelta) {
                decreasing = false;
            }
        }
        if (!increasing && !decreasing) {
            return;
        }
        double latestPh = latest.getLast().phValue().doubleValue();
        boolean sameDirection = increasing ? latestPh > midpoint : latestPh < midpoint;
        if (!sameDirection) {
            return;
        }

        String direction = increasing ? "kiềm" : "axit";
        StringBuilder series = new StringBuilder();
        for (int i = 0; i < latest.size(); i++) {
            if (i > 0) {
                series.append(" → ");
            }
            series.append(latest.get(i).phValue());
        }

        String explanation = ("Xu hướng cần để ý — %d lần quét gần nhất của bé mèo này (%s) đang dịch dần "
                + "về phía %s. Xu hướng thường có ý nghĩa hơn một con số đơn lẻ, vì nó ít bị ảnh hưởng bởi "
                + "điều kiện chụp của từng lần. Bạn có thể thử duy trì quét 1 lần/ngày trong 3 ngày tới để "
                + "xem xu hướng có tiếp diễn không. Nếu bạn vừa đổi thức ăn, hãy ghi chú lại mốc thời gian "
                + "đó. Đây là quan sát từ màu cát, không phải chẩn đoán.")
                .formatted(streak, series, direction);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("direction", direction);
        params.put("phSeries", series.toString());

        raise(rule, event, latest.getFirst().capturedAt(), now, explanation, params);
    }

    // ------------------------------------------------------------------ R4

    private void evaluateLowQualityStreak(ScanSavedEvent event, Instant now) {
        MonitoringRule rule = enabledRule(MonitoringRuleCode.LOW_QUALITY_STREAK.name());
        if (rule == null) {
            return;
        }
        int streak = rule.paramAsInt("streak", 2);
        double maxConfidence = rule.paramAsDouble("maxConfidence", 0.50);

        List<ScanHistoryQuery.RecentScan> scans = scanHistoryQuery.recentValidScans(
                event.catId(), event.capturedAt().minusSeconds(30L * 24 * 3600), streak);
        if (scans.size() < streak) {
            return;
        }
        boolean allLow = scans.stream().limit(streak)
                .allMatch(s -> s.confidence() != null && s.confidence().doubleValue() < maxConfidence);
        if (!allLow) {
            return;
        }

        String explanation = ("Mẹo quét tốt hơn — %d lần quét gần đây có độ tin cậy thấp. Hãy thử: đặt thẻ "
                + "màu tham chiếu vào khung, chụp cách mặt cát 15-25 cm, ở nơi có ánh sáng trắng, và tránh "
                + "để bóng của bạn đổ lên vùng cát.").formatted(streak);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("n", streak);

        Instant windowFrom = scans.get(Math.min(streak, scans.size()) - 1).capturedAt();
        raise(rule, event, windowFrom, now, explanation, params);
    }

    // ------------------------------------------------------------------ dùng chung

    private List<ScanHistoryQuery.RecentScan> validScans(java.util.UUID catId, Instant since, double minConfidence) {
        return scanHistoryQuery.recentValidScans(catId, since, 200).stream()
                .filter(s -> !"INCONCLUSIVE".equals(s.classification()))
                .filter(s -> !s.nearBoundary())
                .filter(s -> s.confidence() != null && s.confidence().doubleValue() >= minConfidence)
                .toList();
    }

    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2))
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private MonitoringRule enabledRule(String code) {
        Optional<MonitoringRule> rule = ruleRepository.findByCode(code);
        return rule.filter(MonitoringRule::isEnabled).orElse(null);
    }

    private void raise(MonitoringRule rule, ScanSavedEvent event, Instant windowFrom, Instant now,
                        String explanationVi, Map<String, Object> params) {
        long bucket = now.getEpochSecond() / Math.max(1, rule.getCooldownHours() * 3600L);
        String dedupeKey = rule.getCode() + ":" + event.catId() + ":" + bucket;

        HealthFlag flag = new HealthFlag(
                uuidV7.generate(), event.catId(), rule.getCode(), event.scanId(),
                rule.getSeverity(), windowFrom, now, rule.getMessageKey(), params,
                explanationVi, dedupeKey, now);
        healthFlagRepository.saveIfNotDuplicate(flag);
    }
}
