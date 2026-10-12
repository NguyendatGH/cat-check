package com.catcheck.insight.application;

import com.catcheck.insight.api.OpenHealthFlagQuery;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class OpenHealthFlagQueryAdapter implements OpenHealthFlagQuery {

    private static final int MAX_LIMIT = 100;

    private final HealthFlagRepository healthFlagRepository;

    public OpenHealthFlagQueryAdapter(HealthFlagRepository healthFlagRepository) {
        this.healthFlagRepository = healthFlagRepository;
    }

    @Override
    public List<OpenFlag> openFlagsFor(UUID userId, UUID catId, int limit) {
        int bounded = Math.min(Math.max(limit, 1), MAX_LIMIT);
        return healthFlagRepository.findByFilter(userId, catId, Boolean.FALSE, null, null, bounded)
                .items().stream()
                .map(flag -> new OpenFlag(flag.getRuleCode(), flag.getSeverity().name(), flag.getTriggeredAt()))
                .toList();
    }
}
