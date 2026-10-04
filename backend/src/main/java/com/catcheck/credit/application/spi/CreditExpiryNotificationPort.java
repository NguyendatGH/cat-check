package com.catcheck.credit.application.spi;

import com.catcheck.credit.domain.ExpiryReminderMilestone;

import java.time.Instant;
import java.util.UUID;

/**
 * Cổng ra để {@code CreditExpiringReminderJob} <b>đẩy thông báo vào outbox</b>, do module
 * {@code notification} hiện thực.
 *
 * <p>p12 §12.6.1 quy tắc 8 nói rõ: <i>"Job không tự gửi thông báo trong thread của mình — mọi
 * thông báo đi qua outbox"</i>, để một SMTP/FCM chậm không kéo dài thời gian giữ khoá ShedLock.
 * Vì vậy hợp đồng ở đây là <b>xếp hàng</b>, không phải gửi: hiện thực chỉ được INSERT vào
 * {@code notification} / {@code email_outbox} trong transaction của bên gọi, và trả về ngay.</p>
 *
 * <p><b>Chưa có hiện thực nào ở thời điểm W1-B.</b> Module {@code notification} hiện chỉ có
 * {@code EmailSender} (gửi trực tiếp, không outbox) — xem {@code handoffs.md} H15.e. Vì thiếu
 * adapter, {@code catcheck.jobs.credit-expiring-reminder.enabled} được đặt {@code false} trong
 * {@code application.yml}: bật một job chắc chắn không gửi được gì chỉ tạo ra một dòng
 * {@code job_run} thất bại mỗi giờ, làm loãng đúng cái cảnh báo mà p12 §12.6.2 muốn dùng.</p>
 */
public interface CreditExpiryNotificationPort {

    /**
     * Xếp một thông báo "credit sắp hết hạn" vào outbox.
     *
     * <p>Hiện thực <b>phải</b> idempotent theo {@code (userId, batchId, milestone)} — cơ chế mà
     * p12 §12.6.2 chỉ định là {@code notification.dedupe_key UNIQUE}; hai cột
     * {@code t48h_notified_at}/{@code t6h_notified_at} trên {@code credit_batch} chỉ là bộ lọc
     * nhanh để job không quét lại, không phải hàng rào chống trùng.</p>
     *
     * @param userId          người nhận
     * @param batchId         lô credit sắp hết hạn
     * @param milestone       mốc T-48h hay T-6h; {@link ExpiryReminderMilestone#templateCode()}
     *                        là mã template p12 §12.3 cần dùng
     * @param expiresAt       mốc hết hạn, để render đếm ngược
     * @param remainingAmount số credit còn chưa dùng của lô
     */
    void queueExpiryReminder(
            UUID userId,
            UUID batchId,
            ExpiryReminderMilestone milestone,
            Instant expiresAt,
            int remainingAmount);
}
