package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.JobRunRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần chạy job — L64 {@code GET /admin/jobs/runs}.
 *
 * <p><b>Có đủ {@code triggerType}, {@code dryRun}, {@code itemsProcessed/Deleted/Failed},
 * {@code instanceId} từ W5-D</b> ({@code V26__job_run_columns.sql} bổ sung các cột p4 §K3 /
 * p12 §12.8.2 mà {@code V15__ops.sql} còn thiếu — handoff H15.44). Trước đó bản DTO này cố ý bỏ
 * trống các trường ấy để UI không hiện ô rỗng như thể job không ghi gì.</p>
 *
 * <p>{@code rowCount} giữ lại bên cạnh {@code itemsProcessed} trong giai đoạn chuyển tiếp — xem
 * {@code JobRunRow}.</p>
 */
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
