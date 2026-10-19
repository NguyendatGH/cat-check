package com.catcheck.insight.domain.port;

import com.catcheck.insight.domain.HealthFlag;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HealthFlagRepository {

    Optional<HealthFlag> saveIfNotDuplicate(HealthFlag flag);

    Optional<HealthFlag> findById(UUID id);

    Page findByFilter(UUID userId, UUID catId, Boolean acknowledged, String severity, String cursor, int limit);

    HealthFlag save(HealthFlag flag);

    List<UUID> deleteByTriggerScanId(UUID scanId);

    boolean isCatOwnedByUser(UUID catId, UUID userId);

    List<HealthFlag> findByCatIdAndTriggeredAtBetween(UUID catId, java.time.Instant from, java.time.Instant to);

    record Page(List<HealthFlag> items, String nextCursor) {
    }
}
