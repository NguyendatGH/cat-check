package com.catcheck.notification.infrastructure;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationErrorCodeConfiguration {

    @Bean
    ErrorCode notificationNotFound() {
        return NotificationErrorCode.NOTIFICATION_NOT_FOUND;
    }

    @Bean
    ErrorCode pushSubscriptionInvalid() {
        return NotificationErrorCode.PUSH_SUBSCRIPTION_INVALID;
    }

    @Bean
    ErrorCode pushSubscriptionLimit() {
        return NotificationErrorCode.PUSH_SUBSCRIPTION_LIMIT;
    }

    @Bean
    ErrorCode notificationTemplateUnknown() {
        return NotificationErrorCode.NOTIFICATION_TEMPLATE_UNKNOWN;
    }
}
