package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.MonitoringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface MonitoringRuleJpaRepository extends JpaRepository<MonitoringRule, String> {

    List<MonitoringRule> findByEnabledTrue();

    /**
     * F4 — danh mục hiển thị cho người dùng, sắp theo {@code sort_order} (p4 D11 có cột này
     * đúng để phục vụ thứ tự hiển thị). Tách khỏi {@link #findByEnabledTrue()} vì bộ máy
     * đánh giá rule không phụ thuộc thứ tự, không đổi hành vi sẵn có của nó.
     */
    List<MonitoringRule> findByEnabledTrueOrderBySortOrderAscCodeAsc();

    /** L38 — kể cả rule đang tắt: màn cấu hình phải thấy được thứ mình vừa tắt. */
    List<MonitoringRule> findAllByOrderBySortOrderAscCodeAsc();
}
