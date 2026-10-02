package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MonitoringRuleRepositoryAdapter implements MonitoringRuleRepository {

    private final MonitoringRuleJpaRepository jpaRepository;

    MonitoringRuleRepositoryAdapter(MonitoringRuleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<MonitoringRule> findAllEnabled() {
        return jpaRepository.findByEnabledTrue();
    }

    @Override
    public List<MonitoringRule> findAllEnabledForDisplay() {
        return jpaRepository.findByEnabledTrueOrderBySortOrderAscCodeAsc();
    }

    @Override
    public Optional<MonitoringRule> findByCode(String code) {
        return jpaRepository.findById(code);
    }

    @Override
    public List<MonitoringRule> findAll() {
        return jpaRepository.findAll();
    }
}
