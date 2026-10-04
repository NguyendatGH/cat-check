package com.catcheck.reminder.domain.port;

import com.catcheck.reminder.domain.Reminder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng lưu trữ {@link Reminder}. Mọi truy vấn <b>của người dùng</b> BẮT BUỘC mang
 * {@code userId} — bất biến I14.
 *
 * <p><b>Ngoại lệ duy nhất: ba phương thức của scheduler</b> ({@link #findDueIds},
 * {@link #lockDue}, {@link #markDispatched}). {@code SendDueRemindersJob} chạy không có phiên
 * đăng nhập và phải quét lịch của <i>mọi</i> người dùng, nên I14 không áp dụng được ở đó; bù
 * lại cả ba đều khoá cứng điều kiện {@code active AND deleted_at IS NULL} (p12 §12.5.5) và
 * chỉ job gọi tới.</p>
 */
public interface ReminderRepository {

    Reminder insert(Reminder reminder);

    /**
     * @return rỗng khi id không tồn tại HOẶC không thuộc {@code userId} — p8 §8.2.5 quy tắc 1:
     *         tài nguyên của người khác trả 404, không trả 403
     */
    Optional<Reminder> findByIdForUser(UUID id, UUID userId);

    List<Reminder> findAllForUser(UUID userId, UUID catIdFilter, Boolean activeFilter);

    Reminder update(Reminder reminder);

    /** Xoá mềm ({@code deleted_at = now()}). */
    void softDelete(UUID id, UUID userId);

    /** Có lịch nào cùng {@code type} đang bật cho mèo này không — phục vụ REMINDER_LIMIT_REACHED. */
    Optional<UUID> findActiveIdByCatAndType(UUID userId, UUID catId, String type);

    /* ------------------------------------------------------- scheduler (SendDueRemindersJob) */

    /**
     * Id các lịch đã tới hạn, cũ nhất trước, nhiều nhất {@code limit} dòng (p12 §12.5.5(a):
     * {@code LIMIT 200}).
     *
     * <p>Chỉ trả id chứ không trả cả bản ghi, và <b>không</b> khoá dòng: khoá thật nằm ở
     * {@link #lockDue} trong transaction của từng lịch. Nếu khoá cả lô ở một transaction dài
     * thì transaction con {@code REQUIRES_NEW} xử lý từng lịch sẽ tự chặn chính mình (cùng
     * luồng, khác connection) — kẹt vĩnh viễn chứ không chỉ chậm.</p>
     *
     * <p>Chỉ {@code type = 'SCAN_ROUTINE'}: p12 §12.5.1 ghi rõ {@code CREDIT_EXPIRY} do
     * {@code CreditExpiringReminderJob} lo, còn {@code SURVEY_FOLLOWUP} chưa có mã template nào
     * trong registry p12 §12.2 (xem handoff H15.89).</p>
     */
    List<UUID> findDueIds(Instant now, int limit);

    /**
     * Khoá đúng một lịch tới hạn bằng {@code FOR UPDATE SKIP LOCKED} (p12 §12.5.5(a)).
     *
     * @return rỗng khi instance khác đang giữ dòng, hoặc khi lịch đã bị tắt/xoá/đẩy lịch giữa
     *         lúc {@link #findDueIds} chạy và lúc khoá — cả ba đều là "bỏ qua", không phải lỗi
     */
    Optional<Reminder> lockDue(UUID id, Instant now);

    /**
     * Đóng một lần gửi: {@code last_run_at = sentAt}, {@code next_run_at = nextRunAt}.
     *
     * <p>Gọi trong CÙNG transaction với việc ghi {@code notification}/outbox — p12 §12.5.5(b):
     * đó là thứ bảo đảm job không bao giờ thấy cùng một lịch "đến hạn" hai lần liên tiếp.</p>
     */
    void markDispatched(UUID id, Instant sentAt, Instant nextRunAt);
}
