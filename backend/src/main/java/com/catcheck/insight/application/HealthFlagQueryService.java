package com.catcheck.insight.application;

import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.insight.domain.HealthFlag;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** {@code GET /health-flags}, {@code GET /health-flags/{id}}, {@code POST .../acknowledge} (p8 §8.4.7 G1-G3). */
@Service
public class HealthFlagQueryService {

    private final HealthFlagRepository healthFlagRepository;
    private final Clock clock;

    public HealthFlagQueryService(HealthFlagRepository healthFlagRepository, Clock clock) {
        this.healthFlagRepository = healthFlagRepository;
        this.clock = clock;
    }

    public HealthFlagRepository.Page list(UUID userId, UUID catId, Boolean acknowledged, String severity,
                                           String cursor, int limit) {
        return healthFlagRepository.findByFilter(userId, catId, acknowledged, severity, cursor,
                Math.min(Math.max(limit, 1), 100));
    }

    public HealthFlag detail(UUID userId, UUID flagId) {
        return requireOwned(userId, flagId);
    }

    @Transactional
    public void acknowledge(UUID userId, UUID flagId) {
        HealthFlag flag = requireOwned(userId, flagId);
        flag.acknowledge(userId, clock.instant());
        healthFlagRepository.save(flag);
    }

    private HealthFlag requireOwned(UUID userId, UUID flagId) {
        HealthFlag flag = healthFlagRepository.findById(flagId)
                .orElseThrow(() -> new NotFoundException(InsightErrorCode.HEALTH_FLAG_NOT_FOUND));
        if (!healthFlagRepository.isCatOwnedByUser(flag.getCatId(), userId)) {
            // 404 chứ không 403 (p8 §8.3.4) - không tiết lộ flag của người khác tồn tại.
            throw new NotFoundException(InsightErrorCode.HEALTH_FLAG_NOT_FOUND);
        }
        return flag;
    }
}
