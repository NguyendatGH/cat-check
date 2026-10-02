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
}
