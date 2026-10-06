package com.catcheck.insight.domain.port;

import com.catcheck.insight.domain.MonitoringRule;

import java.util.List;
import java.util.Optional;

public interface MonitoringRuleRepository {

    List<MonitoringRule> findAllEnabled();

    /** F4 — như {@link #findAllEnabled()} nhưng sắp theo {@code sortOrder} để hiển thị. */
    List<MonitoringRule> findAllEnabledForDisplay();

    Optional<MonitoringRule> findByCode(String code);

    List<MonitoringRule> findAll();

    /** L38 — mọi rule, kể cả rule đang tắt, sắp theo {@code sort_order} để màn admin ổn định. */
    List<MonitoringRule> findAllForAdmin();

    /**
     * L39 — ghi cấu hình đã sửa <b>rồi đọc lại từ DB</b>.
     *
     * <p><b>Bug thật đã sửa, phát hiện bằng curl:</b> {@code monitoring_rule} có trigger
     * {@code trg_monitoring_rule_updated_at} ghi {@code NEW.updated_at = now()} ở phía DB. Entity
     * trong bộ nhớ vẫn giữ giá trị lấy từ {@code Clock} của ứng dụng, lệch với giá trị thật vài
     * phần nghìn giây — nên {@code ETag} trả về trong response của L39 <b>không bao giờ khớp</b>
     * dòng đã lưu, và lần {@code PATCH} kế tiếp luôn {@code 412 RESOURCE_MODIFIED}. Quan sát
     * thật: response trả {@code W/"1791290989845-MONOTONIC_TREND"} còn DB giữ
     * {@code 19:49:49.844659} ⇒ 1791290989844. Vì vậy cổng này đọc lại sau khi ghi; trả
     * {@code save()} trần là mời lỗi đó quay lại.</p>
     */
    MonitoringRule saveAndReload(MonitoringRule rule);
}
