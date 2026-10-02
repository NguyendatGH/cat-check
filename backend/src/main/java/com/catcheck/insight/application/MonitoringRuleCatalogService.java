package com.catcheck.insight.application;

import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * F4 — danh mục rule cảnh báo đang bật, để người dùng hiểu "vì sao tôi bị flag"
 * ({@code GET /reference/monitoring-rules}, p8 §8.4.6).
 *
 * <p>Không {@code @Transactional}: một SELECT trên bảng cấu hình vài dòng.</p>
 */
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
