package com.catcheck.insight.application;

import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.MonitoringRuleCode;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import com.catcheck.insight.api.ClinicalSignEscalation;
import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hiện thực {@link ClinicalSignEscalation} — cảnh báo khẩn p6 §6.9.6. Xem javadoc cổng về việc
 * chưa có nơi gọi thật trong MVP (thiếu endpoint ở module {@code cat}).
 */
@Service
public class ClinicalSignEscalationService implements ClinicalSignEscalation {

    private static final Set<String> BLOCKING_ALONE = Set.of("NO_URINE", "STRAINING");

    private final MonitoringRuleRepository ruleRepository;
    private final HealthFlagRepository healthFlagRepository;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ClinicalSignEscalationService(MonitoringRuleRepository ruleRepository,
                                          HealthFlagRepository healthFlagRepository,
                                          UuidV7 uuidV7, Clock clock) {
        this.ruleRepository = ruleRepository;
        this.healthFlagRepository = healthFlagRepository;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Override
    @Transactional
    public EscalationResult reportClinicalSigns(UUID catId, UUID scanId, UUID clinicalSignReportId, List<String> signs) {
        if (signs == null || signs.isEmpty()) {
            throw new IllegalArgumentException("signs không được rỗng");
        }
        MonitoringRule rule = ruleRepository.findByCode(MonitoringRuleCode.URGENT_CLINICAL_SIGN.name())
                .orElseThrow(() -> new IllegalStateException("Thiếu seed monitoring_rule URGENT_CLINICAL_SIGN"));

        boolean hasAloneBlocker = signs.stream().anyMatch(BLOCKING_ALONE::contains);
        boolean blocking = hasAloneBlocker || signs.size() >= 2;

        String descriptions = String.join(", ", signs);
        String explanation = ("⚠️ Hãy đưa bé đến bác sĩ thú y ngay hôm nay. Bạn cho biết bé mèo này đang "
                + "%s. Đây là tình huống không nên theo dõi tại nhà. Ở mèo, việc rặn tiểu khó hoặc không "
                + "đi tiểu được có thể trở nên nguy hiểm rất nhanh, trong vòng một ngày. Hãy làm ngay bây "
                + "giờ: gọi cho phòng khám thú y gần nhất và mô tả đúng những gì bạn quan sát được. Đừng "
                + "chờ kết quả quét tiếp theo, và đừng chờ tới ngày mai. CatCheck chỉ hỗ trợ theo dõi màu "
                + "cát và không thể đánh giá tình trạng này. Thông tin trên không thay thế việc thăm khám.")
                .formatted(descriptions);

        // p6 §6.9.6 + p4 D12: URGENT_CLINICAL_SIGN không cooldown - dedupe_key dùng
        // clinical_sign_report_id (duy nhất mỗi lần khai) thay vì bucket giờ.
        String dedupeKey = rule.getCode() + ":" + catId + ":" + clinicalSignReportId;
        Instant now = clock.instant();
        HealthFlag flag = new HealthFlag(
                uuidV7.generate(), catId, rule.getCode(), scanId, rule.getSeverity(),
                now, now, rule.getMessageKey(), Map.of("signs", signs), explanation, dedupeKey, now);

        HealthFlag saved = healthFlagRepository.saveIfNotDuplicate(flag)
                .orElseThrow(() -> new BusinessRuleException(InsightErrorCode.HEALTH_FLAG_DUPLICATE));
        return new EscalationResult(saved.getId(), blocking, explanation);
    }
}
