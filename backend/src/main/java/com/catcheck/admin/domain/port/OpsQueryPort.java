package com.catcheck.admin.domain.port;

import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.OutboxRow;

import java.util.List;

/**
 * Cổng đọc hai bảng vận hành cho màn quản trị — L64 ({@code job_run}) và L66
 * ({@code email_outbox} + {@code notification_outbox}).
 *
 * <p><b>Chỉ đọc.</b> L65 (chạy job bằng tay) và L67 (gửi lại một bản ghi {@code FAILED}) cố ý
 * không có ở đây: cả hai là hành động GHI vào miền của module khác
 * ({@code shared.job} / {@code notification}) và phải đi qua cổng của chính module đó, không
 * phải qua một câu {@code UPDATE} từ {@code admin}. Xem handoff H15.106.</p>
 *
 * <p><b>Lệch so với p7 §7.2.3</b> (mỗi module sở hữu bảng của mình): {@code job_run} thuộc
 * {@code shared.job}, hai bảng outbox thuộc {@code notification}. Adapter của {@code admin} đọc
 * trực tiếp vì cả hai module đó nằm ngoài vùng file của gói việc này — handoff H15.103.</p>
 */
public interface OpsQueryPort {

    /**
     * L64 — các lần chạy job, mới nhất trước.
     *
     * @param jobName lọc theo tên job; {@code null} = mọi job
     * @param status  lọc theo trạng thái ({@code RUNNING}/{@code SUCCESS}/{@code FAILED}/
     *                {@code PARTIAL}/{@code SKIPPED}); {@code null} = mọi trạng thái
     */
    List<JobRunRow> findJobRuns(String jobName, String status, int offset, int limit);

    long countJobRuns(String jobName, String status);

    /**
     * L66 — bản ghi outbox, mới nhất trước.
     *
     * @param channel {@code EMAIL}/{@code PUSH}; {@code null} = cả hai
     * @param status  {@code PENDING}/{@code SENT}/{@code FAILED}; {@code null} = mọi trạng thái
     */
    List<OutboxRow> findOutbox(String channel, String status, int offset, int limit);

    long countOutbox(String channel, String status);
}
