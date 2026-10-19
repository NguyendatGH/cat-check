package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.MonitoringRule;
import com.catcheck.insight.domain.port.MonitoringRuleRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class MonitoringRuleRepositoryAdapter implements MonitoringRuleRepository {

    private final MonitoringRuleJpaRepository jpaRepository;
    private final EntityManager entityManager;

    MonitoringRuleRepositoryAdapter(MonitoringRuleJpaRepository jpaRepository,
                                    EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
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

    @Override
    public List<MonitoringRule> findAllForAdmin() {
        return jpaRepository.findAllByOrderBySortOrderAscCodeAsc();
    }

    @Override
    public MonitoringRule saveAndReload(MonitoringRule rule) {
        MonitoringRule saved = jpaRepository.saveAndFlush(rule);
        entityManager.refresh(saved);
        return saved;
    }
}
