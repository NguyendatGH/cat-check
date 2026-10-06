package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần chạy job — dòng {@code job_run} (p4 §K3), cho L64 {@code GET /admin/jobs/runs}.
 *
 * <p>p8 L64 ghi rõ {@code job_run} là <b>bảng log job DUY NHẤT</b> (H9.1) — không có bảng thứ
 * hai để đối chiếu, nên màn này là toàn bộ cái nhìn của người vận hành về job nền.</p>
 *
 * <p><b>H15.44 đã xong (W5-D).</b> Trước đây record này chỉ có 8 trường vì {@code V15__ops.sql}
 * tạo bảng hẹp hơn p4 §K3 / p12 §12.8.2. {@code V26__job_run_columns.sql} bổ sung
 * {@code trigger_type}, {@code dry_run}, {@code items_processed/deleted/failed},
 * {@code instance_id} nên L64 hiển thị được đúng những gì p14 §14.2.2 ô Q26 cần — đặc biệt là
 * câu <i>"job này tự chạy theo lịch hay có người bấm?"</i>, vốn là lý do {@code trigger_type}
 * tồn tại.</p>
 *
 * <p>{@code rowCount} vẫn ở đây bên cạnh {@code itemsProcessed} vì cột cũ chưa bị xoá (xem
 * javadoc {@code JdbcJobRunAdapter}); hai trường mang cùng giá trị trong giai đoạn chuyển tiếp,
 * và handoff H15.180 ghi việc gỡ cột cũ.</p>
 *
 * @param errorSummary KHÔNG chứa PII (p11 §11.10.3) và không stack trace (p4 §K3). Từ V26 nó chỉ
 *                     còn chứa tóm tắt lỗi thật — không còn bị nhồi {@code dry-run}/
 *                     {@code trigger=…} như bản xuống thang trước đó
 * @param instanceId   hostname/container id — máy chủ, KHÔNG phải thiết bị người dùng
 */
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
