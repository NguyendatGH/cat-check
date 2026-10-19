package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

public record JobRunRow(
        UUID id,
        String jobName,
        String status,
        String triggerType,
        Boolean dryRun,
        Instant startedAt,
        Instant finishedAt,
        Integer durationMs,
        Integer rowCount,
        Integer itemsProcessed,
        Integer itemsDeleted,
        Integer itemsFailed,
        String instanceId,
        String errorSummary
) {
}
