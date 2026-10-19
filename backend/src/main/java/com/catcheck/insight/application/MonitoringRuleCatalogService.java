package com.catcheck.insight.application;

import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MonitoringRuleCatalogService {

    private final MonitoringRuleRepository ruleRepository;

    public MonitoringRuleCatalogService(MonitoringRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public List<MonitoringRule> listEnabledRules() {
        return ruleRepository.findAllEnabledForDisplay();
    }
}
