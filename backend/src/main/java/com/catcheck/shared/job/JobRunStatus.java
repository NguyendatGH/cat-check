package com.catcheck.shared.job;

/**
 * Trạng thái một lần chạy job — {@code job_run.status} (p4 §K3, p12 §12.8.2).
 *
 * <p><b>{@link #SKIPPED_THRESHOLD} KHÔNG phải {@link #FAILED}.</b> Job dừng vì chạm ngưỡng an
 * toàn 20% (p15 REQ-RET-02) là hệ thống hoạt động ĐÚNG — nó vừa chặn một lần xoá bất thường.
 * Gộp vào {@code FAILED} khiến người trực lướt qua nó như một lỗi kỹ thuật thường gặp, đúng lúc
 * cần chú ý nhất (p4 §K3 ghi chú nghiệp vụ).</p>
 */
public enum JobRunStatus {

    /** Đang chạy; {@code finished_at} còn NULL. */
    RUNNING,

    /** Chạy xong, không có item nào lỗi. */
    SUCCESS,

    /** Lỗi kỹ thuật làm cả lần chạy hỏng. */
    FAILED,

    /** Chạy xong nhưng một phần item lỗi (p12 §12.6.2: "lỗi 1 batch không chặn batch khác"). */
    PARTIAL,

    /** Dừng vì chạm ngưỡng an toàn 20% của p15 REQ-RET-02 — không xoá gì. */
    SKIPPED_THRESHOLD
}
