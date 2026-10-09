package com.catcheck.reminder.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Cổng đọc lịch nhắc ĐANG BẬT của một mèo, dùng cho ngữ cảnh trợ lý AI (module {@code ai}).
 *
 * <p>Cố ý KHÔNG kiểm {@code ReminderFeaturePort.isReminderEnabled} như
 * {@code ReminderService} làm cho các thao tác CRUD: đây là một lần ĐỌC phục vụ hiển thị ngữ
 * cảnh, không phải tạo/sửa lịch mới. Nếu chặn ở đây, một tài khoản vừa hạ gói sẽ khiến trợ lý
 * "quên" mất lịch nhắc vẫn đang chạy thật ở backend — sai lệch thông tin còn rủi ro hơn là hiển
 * thị một lịch nhắc cho người dùng không còn trong gói có tính năng nhắc.</p>
 */
public interface ActiveReminderQuery {

    List<ActiveReminder> activeRemindersFor(UUID userId, UUID catId);

    /** {@code type} thô theo {@code reminder.domain.ReminderType} (vd {@code SCAN_ROUTINE}). */
    record ActiveReminder(String type, Instant nextRunAt) {
    }
}
