package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.ExpiryReminderMilestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code credit_batch} — màn số dư, màn chi tiết lô, và tạo lô mới khi kích hoạt.
 *
 * <p>{@link #insertNewBatch(CreditBatchGrant)} chạy trong transaction của use case gọi (ranh giới
 * transaction thuộc tầng {@code application}, p7 §7.3.2). Các phương thức đọc không khoá dòng
 * nào — chỉ {@link CreditLedgerPort#lockLiveBatchesForFefo} khoá, vì chỉ đường ghi mới cần.</p>
 */
public interface CreditBatchPort {

    /**
     * Tạo một lô credit mới từ một lần kích hoạt gói. Phải đi kèm dòng ledger
     * {@code GRANT} trong CÙNG transaction, không thì bất biến I1 vi phạm.
     */
    void insertNewBatch(CreditBatchGrant batch);

    /**
     * Các lô CÒN HIỆU LỰC của user, sắp theo {@code expires_at} tăng dần (FEFO) — cùng thứ tự
     * với đường ghi để người dùng nhìn thấy đúng thứ tự tiêu credit sẽ diễn ra (p5 R4/R5.8).
     */
    List<CreditBatchView> findLiveBatches(UUID userId, Instant now);

    /** Tất cả lô của user kể cả đã đóng — dùng cho đối soát, không dùng cho hiển thị. */
    List<CreditBatchView> findAllBatches(UUID userId);
    /**
     * Id các lô <b>đã quá hạn mà chưa được đóng</b>: {@code status = 'ACTIVE' AND expires_at <=
     * now AND remaining_amount > 0}, sắp theo {@code expires_at} rồi {@code id}, giới hạn
     * {@code limit} dòng.
     *
     * <p>Đây là bước "liệt kê việc" của {@code ExpireCreditBatchesJob} (p12 §12.6.2). Cố ý
     * <b>không khoá</b> và cố ý chỉ trả id: p12 §12.6.2 yêu cầu <i>mỗi lô một transaction
     * riêng</i>, nên khoá ở đây sẽ giữ cả danh sách suốt thời gian xử lý lô cuối cùng và chặn
     * mọi lần trừ credit đang chạy. Việc khoá từng dòng thuộc
     * {@link CreditLedgerPort#lockBatchForExpiry}.</p>
     *
     * <p>Danh sách có thể "lạc hậu" ngay sau khi trả về (một lô vừa bị instance khác đóng) —
     * điều đó vô hại vì bộ lọc idempotent được lặp lại lúc khoá dòng.</p>
     */
    List<UUID> findDueForExpiry(Instant now, int limit);

    /**
     * Đếm số lô đủ điều kiện hết hạn — dùng cho lần chạy {@code dry_run} (p15 REQ-RET-01) và
     * cho con số "còn bao nhiêu dòng quá hạn chưa xử lý" ở màn giám sát p14.
     */
    int countDueForExpiry(Instant now);

    /**
     * Id các lô tới mốc nhắc {@code milestone} mà chưa gửi: còn hiệu lực
     * ({@code status = 'ACTIVE' AND expires_at > now AND remaining_amount > 0}),
     * {@code expires_at <= now + milestone.lead()}, và cột cờ tương ứng còn NULL.
     *
     * <p>Điều kiện {@code expires_at > now} là bắt buộc: lô đã quá hạn không còn gì để nhắc —
     * nó thuộc {@code ExpireCreditBatchesJob}. Cột cờ ({@code t48h_notified_at} /
     * {@code t6h_notified_at}) là bộ lọc idempotent mà p12 §12.6.2 chỉ định.</p>
     */
    List<UUID> findDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now, int limit);

    /** Đếm số lô tới mốc nhắc mà chưa gửi — dùng cho {@code dry_run}. */
    int countDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now);
}
