package com.catcheck.notification.infrastructure;

import com.catcheck.notification.application.NotificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Nạp {@link NotificationProperties}.
 *
 * <p>Phải khai tường minh: dự án không có {@code @ConfigurationPropertiesScan} ở
 * {@code CatCheckApplication} (xem javadoc {@code shared.i18n.LocaleConfig} — cùng lý do, cùng
 * cách xử lý). Thiếu annotation này thì context ném {@code NoSuchBeanDefinitionException} lúc
 * khởi động chứ không phải lỗi biên dịch.</p>
 */
@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfiguration {
}
