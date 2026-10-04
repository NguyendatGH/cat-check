package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần chạy job — dòng {@code job_run} (p4 §K3), cho L64 {@code GET /admin/jobs/runs}.
 *
 * <p>p8 L64 ghi rõ {@code job_run} là <b>bảng log job DUY NHẤT</b> (H9.1) — không có bảng thứ
 * hai để đối chiếu, nên màn này là toàn bộ cái nhìn của người vận hành về job nền.</p>
 *
 * <p><b>Thiếu so với p4 §K3/p12 §12.8.2:</b> {@code V15__ops.sql} hiện không có
 * {@code trigger_type}, {@code dry_run}, {@code items_processed/deleted/failed},
 * {@code instance_id}, và {@code ck_job_run_status} không có {@code SKIPPED_THRESHOLD} — đã ghi
 * handoff H15.44 ở đợt trước. Record này chỉ phản ánh các cột THẬT CÓ; không bịa trường rỗng để
 * UI tưởng là có dữ liệu.</p>
 *
 * @param errorSummary KHÔNG chứa PII (p11 §11.10.3). {@code JdbcJobRunAdapter} hiện nhồi thêm
 *                     một ít số liệu vào đây vì thiếu cột — xem H15.44
 */
public record JobRunRow(
        UUID id,
        String jobName,
        String status,
        Instant startedAt,
        Instant finishedAt,
        Integer durationMs,
        Integer rowCount,
        String errorSummary
) {
}
