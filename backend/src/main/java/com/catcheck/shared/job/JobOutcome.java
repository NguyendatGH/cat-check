package com.catcheck.shared.job;

/**
 * Kết quả một lần chạy job, đúng bộ cột đếm của {@code job_run} (p12 §12.8.2).
 *
 * <p>{@code itemsDeleted} tách khỏi {@code itemsProcessed} là cố ý (p4 §K3): với job retention,
 * "đã xét 10.000, đã xoá 3" và "đã xét 10.000, đã xoá 10.000" là hai tình huống khác hẳn nhau.
 * Job không xoá gì (ví dụ {@code ExpireCreditBatchesJob}) để {@code itemsDeleted = 0}.</p>
 *
 * @param status         trạng thái kết thúc; không bao giờ {@link JobRunStatus#RUNNING}
 * @param itemsProcessed số bản ghi đã xét
 * @param itemsDeleted   số bản ghi/số file đã xoá
 * @param itemsFailed    số bản ghi lỗi — {@code > 0} kéo trạng thái về {@link JobRunStatus#PARTIAL}
 * @param errorSummary   tóm tắt NGẮN, <b>không stack trace</b> (p4 §K3) và không PII
 */
public record JobOutcome(
        JobRunStatus status,
        int itemsProcessed,
        int itemsDeleted,
        int itemsFailed,
        String errorSummary
) {

    /** Trần độ dài tóm tắt lỗi — cột là TEXT nhưng p4 §K3 yêu cầu "rút gọn". */
    public static final int MAX_ERROR_SUMMARY_LENGTH = 500;

    public JobOutcome {
        if (status == null) {
            throw new IllegalArgumentException("jobOutcome.status phải có giá trị");
        }
        if (status == JobRunStatus.RUNNING) {
            throw new IllegalArgumentException("jobOutcome không được mang trạng thái RUNNING");
        }
        if (itemsProcessed < 0 || itemsDeleted < 0 || itemsFailed < 0) {
            throw new IllegalArgumentException("jobOutcome: số đếm không được âm (ck_job_run_count)");
        }
        errorSummary = truncate(errorSummary);
    }

    /**
     * Kết quả của một job chỉ đọc/chỉ cập nhật: {@code SUCCESS} nếu không có item nào lỗi,
     * {@code PARTIAL} nếu có. Đây là khuôn mà p12 §12.6.2 mô tả cho nhóm A ("lỗi 1 batch không
     * chặn batch khác") nên mọi job dùng chung thay vì tự quyết trạng thái.
     */
    public static JobOutcome of(int itemsProcessed, int itemsDeleted, int itemsFailed, String errorSummary) {
        JobRunStatus status = itemsFailed == 0 ? JobRunStatus.SUCCESS : JobRunStatus.PARTIAL;
        return new JobOutcome(status, itemsProcessed, itemsDeleted, itemsFailed, errorSummary);
    }

    /** Lần chạy sạch, không item nào lỗi và không xoá gì. */
    public static JobOutcome success(int itemsProcessed) {
        return new JobOutcome(JobRunStatus.SUCCESS, itemsProcessed, 0, 0, null);
    }

    /** Cả lần chạy hỏng vì lỗi kỹ thuật. */
    public static JobOutcome failed(String errorSummary) {
        return new JobOutcome(JobRunStatus.FAILED, 0, 0, 0, errorSummary);
    }

    /** Dừng vì chạm ngưỡng an toàn 20% (p15 REQ-RET-02) — chưa xoá gì. */
    public static JobOutcome skippedThreshold(int itemsProcessed, String errorSummary) {
        return new JobOutcome(JobRunStatus.SKIPPED_THRESHOLD, itemsProcessed, 0, 0, errorSummary);
    }

    private static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= MAX_ERROR_SUMMARY_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_SUMMARY_LENGTH);
    }
}
