package com.catcheck.shared.job;

/**
 * Vì sao một lần chạy job xảy ra — {@code job_run.trigger_type} (p4 §K3, p12 §12.8.2).
 *
 * <p>Phân biệt ba nguồn là bắt buộc để màn "Log job nền" của p14 trả lời được câu
 * <i>"job này tự chạy theo lịch hay có người bấm?"</i> — hai tình huống cần hai cách xử lý khác
 * nhau khi hậu kiểm sự cố.</p>
 */
public enum JobTriggerType {

    /** Scheduler nội bộ ({@code @Scheduled} + ShedLock) kích hoạt theo cron ở p12 §12.6. */
    SCHEDULE,

    /** Admin bấm "chạy lại"/"dry run" ở p14 (Q29, p15 REQ-RET-05). */
    MANUAL,

    /** Chạy theo sự kiện nghiệp vụ (Spring Modulith Event Publication Registry), không cron. */
    EVENT
}
