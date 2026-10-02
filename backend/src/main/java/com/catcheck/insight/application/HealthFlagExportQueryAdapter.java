package com.catcheck.insight.application;

import com.catcheck.insight.api.HealthFlagExportQuery;
import com.catcheck.insight.domain.port.HealthFlagRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Hiện thực {@link HealthFlagExportQuery} (cổng do {@code insight} công bố cho {@code export}). */
@Component
public class HealthFlagExportQueryAdapter implements HealthFlagExportQuery {

    private final HealthFlagRepository healthFlagRepository;

    public HealthFlagExportQueryAdapter(HealthFlagRepository healthFlagRepository) {
        this.healthFlagRepository = healthFlagRepository;
    }

    @Override
    public List<ExportFlagRow> flagsInRange(UUID catId, Instant from, Instant to) {
        return healthFlagRepository.findByCatIdAndTriggeredAtBetween(catId, from, to).stream()
                .map(f -> new ExportFlagRow(f.getRuleCode(), f.getSeverity().name(), f.getExplanationVi(), f.getTriggeredAt()))
                .toList();
    }
}
