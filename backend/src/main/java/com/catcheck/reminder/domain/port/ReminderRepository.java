package com.catcheck.reminder.domain.port;

import com.catcheck.reminder.domain.Reminder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Cổng lưu trữ {@link Reminder}. Mọi truy vấn BẮT BUỘC mang {@code userId} — bất biến I14. */
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
}
