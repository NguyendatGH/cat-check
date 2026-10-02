package com.catcheck.reminder.infrastructure;

import com.catcheck.reminder.api.ReminderErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link ReminderErrorCode} vào {@code ErrorCodeRegistry} — mỗi hằng một bean riêng.
 *
 * <p><b>Hai hằng CỐ Ý không đăng ký</b> (đã grep toàn bộ {@code *ErrorCodeConfiguration.java}):</p>
 * <ul>
 *   <li>{@code FEATURE_NOT_IN_PLAN} — {@code CreditErrorCodeConfiguration.featureNotInPlan()} đã
 *       đăng ký.</li>
 *   <li>{@code CAT_NOT_FOUND} — {@code CatErrorCodeConfiguration} đã đăng ký.</li>
 * </ul>
 * <p>Đăng ký lần nữa sẽ làm context sập lúc khởi động. Exception vẫn hoạt động bình thường:
 * {@code GlobalExceptionHandler} đọc {@code code()}/{@code status()}/{@code typeUri()} trực tiếp
 * trên instance bị ném, registry chỉ kiểm trùng lúc khởi động chứ không phải bảng tra runtime.</p>
 */
@Configuration
public class ReminderErrorCodeConfiguration {

    @Bean
    ErrorCode reminderNotFound() {
        return ReminderErrorCode.REMINDER_NOT_FOUND;
    }

    @Bean
    ErrorCode reminderLimitReached() {
        return ReminderErrorCode.REMINDER_LIMIT_REACHED;
    }

    @Bean
    ErrorCode reminderScheduleInvalid() {
        return ReminderErrorCode.REMINDER_SCHEDULE_INVALID;
    }
}
