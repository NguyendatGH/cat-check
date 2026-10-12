package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.JobRunRow;

import java.time.Instant;
import java.util.UUID;

public record JobRunResponse(
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

    public static JobRunResponse from(JobRunRow row) {
        return new JobRunResponse(
                row.id(), row.jobName(), row.status(), row.triggerType(), row.dryRun(),
                row.startedAt(), row.finishedAt(), row.durationMs(), row.rowCount(),
                row.itemsProcessed(), row.itemsDeleted(), row.itemsFailed(),
                row.instanceId(), row.errorSummary());
    }
}
