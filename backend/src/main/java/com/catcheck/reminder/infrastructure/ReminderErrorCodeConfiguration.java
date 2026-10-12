package com.catcheck.reminder.infrastructure;

import com.catcheck.reminder.api.ReminderErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
