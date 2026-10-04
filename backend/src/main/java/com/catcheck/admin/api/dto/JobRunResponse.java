package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.JobRunRow;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần chạy job — L64 {@code GET /admin/jobs/runs}.
 *
 * <p>Không có {@code triggerType}, {@code dryRun}, {@code itemsProcessed/Deleted/Failed},
 * {@code instanceId}: {@code V15__ops.sql} chưa có các cột đó dù p4 §K3 và p12 §12.8.2 yêu cầu
 * (handoff H15.44). Trả trường rỗng sẽ làm UI hiện ô trống như thể job không ghi gì, khó chẩn
 * đoán hơn là không có trường.</p>
 */
public record JobRunResponse(
        UUID id,
        String jobName,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Integer durationMs,
        Integer rowCount,
        String errorSummary
) {

    public static JobRunResponse from(JobRunRow row) {
        return new JobRunResponse(
                row.id(), row.jobName(), row.status(), row.startedAt(), row.finishedAt(),
                row.durationMs(), row.rowCount(), row.errorSummary());
    }
}
