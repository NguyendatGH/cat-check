package com.catcheck.notification.application;

public record OutboxDispatchReport(int processed, int sent, int retried, int failed, int skipped) {

    public static OutboxDispatchReport empty() {
        return new OutboxDispatchReport(0, 0, 0, 0, 0);
    }

    public static OutboxDispatchReport allSkipped(int processed) {
        return new OutboxDispatchReport(processed, 0, 0, 0, processed);
    }
}
