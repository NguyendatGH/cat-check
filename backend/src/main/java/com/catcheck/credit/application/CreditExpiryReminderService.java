package com.catcheck.credit.application;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Xếp thông báo "credit sắp hết hạn" vào outbox cho một lô — nghiệp vụ của
 * {@code CreditExpiringReminderJob} (p5 R3, p12 §12.6.2).
 *
 * <p>Việc ghi outbox và việc đánh cờ {@code t48h_notified_at}/{@code t6h_notified_at} nằm trong
 * <b>cùng một transaction</b> (cùng khuôn mà p12 §12.5.5 đặt cho {@code SendDueRemindersJob}):
 * nếu cờ được đánh ở transaction khác thì có một cửa sổ mà cờ đã bật nhưng thông báo chưa vào
 * outbox, và người dùng sẽ không bao giờ được nhắc — bỏ sót nguy hiểm hơn trùng lặp (p12 §12.8).</p>
 */
@Service
public class CreditExpiryReminderService {

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;

    /**
     * {@link ObjectProvider} thay vì inject thẳng, vì <b>chưa module nào hiện thực cổng này</b>
     * (xem {@code handoffs.md} H15.e). Inject thẳng sẽ làm toàn bộ ApplicationContext không
     * khởi động được — tức là một tính năng chưa xong ở module notification sẽ hạ cả ứng dụng.
     * Dùng provider: thiếu adapter chỉ làm đúng job này báo lỗi, và báo bằng một câu đọc được.
     */
    private final ObjectProvider<CreditExpiryNotificationPort> notificationPort;

    public CreditExpiryReminderService(
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            ObjectProvider<CreditExpiryNotificationPort> notificationPort
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.notificationPort = notificationPort;
    }

    /**
     * Có adapter outbox để gửi hay chưa. Job gọi hàm này TRƯỚC khi quét, để không mở một lần
     * chạy chắc chắn thất bại.
     */
    public boolean notificationAvailable() {
        return notificationPort.getIfAvailable() != null;
    }

    /** Id các lô tới mốc nhắc mà chưa gửi, nhiều nhất {@code limit} dòng. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDueBatchIds(ExpiryReminderMilestone milestone, Instant now, int limit) {
        return creditBatchPort.findDueForExpiryReminder(milestone, now, limit);
    }

    /** Số lô tới mốc nhắc mà chưa gửi — dùng cho lần chạy {@code dry_run}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int countDueBatches(ExpiryReminderMilestone milestone, Instant now) {
        return creditBatchPort.countDueForExpiryReminder(milestone, now);
    }

    /**
     * Xếp thông báo cho đúng một lô, trong transaction riêng.
     *
     * @return {@code true} nếu vừa xếp được; {@code false} nếu lô đã được nhắc ở mốc này, không
     *         còn đủ điều kiện, hoặc đang bị khoá ({@code SKIP LOCKED})
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean queueReminder(UUID batchId, ExpiryReminderMilestone milestone, Instant now) {
        Optional<ExpiringCreditBatch> locked =
                creditLedgerPort.lockBatchForExpiryReminder(batchId, milestone, now);
        if (locked.isEmpty()) {
            return false;
        }

        CreditExpiryNotificationPort outbox = notificationPort.getIfAvailable();
        if (outbox == null) {
            throw new IllegalStateException(
                    "Thieu adapter CreditExpiryNotificationPort — module notification chua co outbox port (H15.e)");
        }

        ExpiringCreditBatch batch = locked.get();
        outbox.queueExpiryReminder(
                batch.userId(), batch.id(), milestone, batch.expiresAt(), batch.remainingAmount());

        switch (milestone) {
            case T48H -> creditLedgerPort.markT48hNotified(batch.id());
            case T6H -> creditLedgerPort.markT6hNotified(batch.id());
        }
        return true;
    }
}
