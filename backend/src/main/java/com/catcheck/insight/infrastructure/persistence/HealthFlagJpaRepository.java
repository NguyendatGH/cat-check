package com.catcheck.insight.infrastructure.persistence;

import com.catcheck.insight.domain.HealthFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface HealthFlagJpaRepository extends JpaRepository<HealthFlag, UUID> {

    List<HealthFlag> findByTriggerScanId(UUID scanId);

    List<HealthFlag> findByCatIdAndTriggeredAtBetweenOrderByTriggeredAtDesc(UUID catId, Instant from, Instant to);
}
