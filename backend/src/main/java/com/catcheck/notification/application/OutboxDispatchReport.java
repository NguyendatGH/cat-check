package com.catcheck.notification.application;

/**
 * Kết quả một lượt đẩy outbox — số liệu cho {@code job_run.items_processed}/{@code items_failed}
 * (p12 §12.8.2) khi nền job dùng chung ở {@code shared} sẵn sàng.
 *
 * @param skipped bị bỏ qua vì kênh chưa được cấu hình; <b>không</b> tính là một lần thử nên
 *                dòng outbox giữ nguyên {@code PENDING}
 */
public record OutboxDispatchReport(int processed, int sent, int retried, int failed, int skipped) {

    public static OutboxDispatchReport empty() {
        return new OutboxDispatchReport(0, 0, 0, 0, 0);
    }

    public static OutboxDispatchReport allSkipped(int processed) {
        return new OutboxDispatchReport(processed, 0, 0, 0, processed);
    }
}
