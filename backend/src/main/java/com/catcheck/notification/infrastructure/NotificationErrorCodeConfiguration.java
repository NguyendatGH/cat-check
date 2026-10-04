package com.catcheck.notification.infrastructure;

import com.catcheck.notification.api.NotificationErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link NotificationErrorCode} vào {@code ErrorCodeRegistry} — mỗi hằng một bean.
 *
 * <p><b>Ba hằng CỐ Ý không đăng ký</b> (đã grep toàn bộ {@code *ErrorCodeConfiguration.java}):
 * {@code CONSENT_REQUIRED} và {@code PAGINATION_CURSOR_INVALID} đã được
 * {@code PrivacyErrorCodeConfiguration} đăng ký, {@code VALIDATION_FAILED} đã được module khác
 * đăng ký. Đăng ký lần nữa làm {@code ErrorCodeRegistry.validate} ném
 * {@code IllegalStateException} và context sập lúc khởi động. Việc ném exception vẫn hoạt động
 * bình thường: {@code GlobalExceptionHandler} đọc {@code code()}/{@code status()}/
 * {@code typeUri()} trực tiếp trên instance bị ném, registry chỉ kiểm trùng lúc khởi động.</p>
 */
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
