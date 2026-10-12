package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.MonitoringRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface MonitoringRuleJpaRepository extends JpaRepository<MonitoringRule, String> {

    List<MonitoringRule> findByEnabledTrue();

    List<MonitoringRule> findByEnabledTrueOrderBySortOrderAscCodeAsc();

    List<MonitoringRule> findAllByOrderBySortOrderAscCodeAsc();
}
