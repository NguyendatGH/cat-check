package com.catcheck.credit.domain;

import java.time.Duration;

/**
 * Hai mốc nhắc trước khi lô credit hết hạn — <b>T-48h</b> và <b>T-6h</b> (p5 R3, p12 §12.6.2).
 *
 * <p>Mỗi mốc có một cột cờ riêng trên {@code credit_batch} ({@code t48h_notified_at} /
 * {@code t6h_notified_at}, p4 §4.5.1) nên hai mốc không chặn nhau: một lô đã nhắc T-48h vẫn
 * phải nhận nhắc T-6h.</p>
 *
 * <p>Hai cột cờ đó là <b>bộ lọc nhanh</b> để job quét mỗi giờ không gửi lại, KHÔNG phải cơ chế
 * chống trùng thật sự — việc đó thuộc {@code notification.dedupe_key UNIQUE} ở module
 * notification (p12 §12.6.2, javadoc {@code CreditLedgerPort#markT48hNotified}).</p>
 */
public enum ExpiryReminderMilestone {

    /** Còn 48 giờ tới {@code expires_at} — template {@code CREDIT_EXPIRING_T48H}. */
    T48H(Duration.ofHours(48), "CREDIT_EXPIRING_T48H"),

    /** Còn 6 giờ tới {@code expires_at} — template {@code CREDIT_EXPIRING_T6H}. */
    T6H(Duration.ofHours(6), "CREDIT_EXPIRING_T6H");

    private final Duration lead;
    private final String templateCode;

    ExpiryReminderMilestone(Duration lead, String templateCode) {
        this.lead = lead;
        this.templateCode = templateCode;
    }

    /** Khoảng thời gian trước {@code expires_at} mà mốc này được kích hoạt. */
    public Duration lead() {
        return lead;
    }

    /**
     * Mã template thông báo ở p12 §12.3 — module notification tra bảng template bằng mã này.
     * Đặt trong enum để tên mã nằm cạnh mốc thời gian sinh ra nó, thay vì rải trong job.
     */
    public String templateCode() {
        return templateCode;
    }
}
