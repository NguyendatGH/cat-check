package com.catcheck.shared.job;

import java.time.Instant;
import java.util.UUID;

/**
 * Những gì thân job được biết về lần chạy hiện tại.
 *
 * <p>{@link #runId()} là {@code job_run.id} và được truyền tường minh vì nó là
 * <b>tham chiếu nghiệp vụ</b>, không chỉ là id log: {@code credit_ledger.ref_type = 'JOB'} đòi
 * {@code ref_id = job_run.id} (p4 §4.4.6, enum {@code CreditLedgerRefType.JOB}). Không có nó,
 * dòng {@code EXPIRE} không truy ngược được về lần chạy đã sinh ra nó.</p>
 *
 * @param runId     id dòng {@code job_run} vừa mở
 * @param jobName   đúng tên job ở p12 §12.6, không viết tắt
 * @param startedAt mốc bắt đầu, lấy từ {@code Clock} đã inject (R13)
 * @param dryRun    chỉ đếm, không ghi/không xoá (p15 REQ-RET-01)
 */
public record JobContext(UUID runId, String jobName, Instant startedAt, boolean dryRun) {

    public JobContext {
        if (runId == null || jobName == null || startedAt == null) {
            throw new IllegalArgumentException("jobContext thiếu trường bắt buộc");
        }
    }
}
