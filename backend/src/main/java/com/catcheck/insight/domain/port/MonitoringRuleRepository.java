package com.catcheck.insight.domain.port;

import com.catcheck.insight.domain.MonitoringRule;

import java.util.List;
import java.util.Optional;

public interface MonitoringRuleRepository {

    List<MonitoringRule> findAllEnabled();

    List<MonitoringRule> findAllEnabledForDisplay();

    Optional<MonitoringRule> findByCode(String code);

    List<MonitoringRule> findAll();

    List<MonitoringRule> findAllForAdmin();

    MonitoringRule saveAndReload(MonitoringRule rule);
}
