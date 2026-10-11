package com.catcheck.shared.i18n;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Cấu hình nguồn message (messages/errors, vi + en) và cách xác định locale của request
 * (mặc định qua header {@code Accept-Language}, rơi về {@code catcheck.i18n.default-locale}
 * khi client không gửi header).
 *
 * <p>{@code @EnableConfigurationProperties(I18nProperties.class)} — không có annotation này thì
 * {@code I18nProperties} (record {@code @ConfigurationProperties}) không được đăng ký làm bean, và
 * constructor dưới đây ném {@code NoSuchBeanDefinitionException} lúc khởi động context. Không có
 * {@code @ConfigurationPropertiesScan} nào ở {@code CatCheckApplication} bao trùm gói này, nên phải
 * tự bật ở đây (phát hiện qua smoke test khởi động context thật — trước đó không ai kiểm).</p>
 */
@Configuration
@EnableConfigurationProperties(I18nProperties.class)
public class LocaleConfig {

    private final I18nProperties i18nProperties;

    public LocaleConfig(I18nProperties i18nProperties) {
        this.i18nProperties = i18nProperties;
    }

    /**
     * <b>Bug thật đã sửa:</b> {@code classpath:messages/content} thiếu khỏi danh sách basenames
     * bên dưới dù {@code content_vi.properties}/{@code content_en.properties} được ContentErrorCode
     * khai là nơi tra dịch — mọi lỗi nghiệp vụ của module {@code content} (L40–L45: not-found,
     * source-required, self-approval-forbidden, not-editable, invalid-state, slug-taken) rơi vào
     * nhánh {@code setUseCodeAsDefaultMessage(true)} và trả thẳng tên hằng số enum (ví dụ
     * {@code "CONTENT_NOT_FOUND"}) cho client thay vì câu đã dịch.
     *
     * <p><b>Rủi ro còn lại (đã kiểm, CHƯA sửa vì phạm vi đổi vượt ra ngoài {@code shared}):</b>
     * {@link ReloadableResourceBundleMessageSource} chỉ dùng basename ĐẦU TIÊN trong danh sách có
     * chứa khoá đang tra — nếu hai module khác nhau tự đặt {@code ErrorCode#code()} trùng tên (ví
     * dụ {@code VALIDATION_FAILED} ở {@code cat}/{@code identity}/{@code privacy}/{@code colorchart},
     * hay {@code REASON_REQUIRED} ở {@code colorchart} và {@code content}), thì MỌI module phía sau
     * trong danh sách này đều âm thầm nhận nhầm câu dịch của module đứng trước — xác nhận bằng test
     * thủ công, không phải suy đoán. Sửa triệt để cần namespace hoá khoá dịch theo module (đụng tới
     * mọi enum {@code ErrorCode} của từng module, ngoài phạm vi {@code shared/content/audit}), nên
     * chỉ ghi nhận ở đây; xem việc "W3 gộp lại" đã được các enum liên quan tự ghi chú.</p>
     */
    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasenames(
                "classpath:messages/messages",
                "classpath:messages/errors",
                "classpath:messages/identity",
                "classpath:messages/privacy",
                "classpath:messages/cat",
                "classpath:messages/credit",
                "classpath:messages/colorchart",
                "classpath:messages/scan",
                "classpath:messages/insight",
                "classpath:messages/export",
                "classpath:messages/reminder",
                "classpath:messages/notification",
                "classpath:messages/community",
                "classpath:messages/shop",
                "classpath:messages/ai",
                "classpath:messages/content",
                "classpath:messages/admin",
                "classpath:messages/media",
                "classpath:messages/place");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        messageSource.setDefaultLocale(i18nProperties.defaultLocale());
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(i18nProperties.defaultLocale());
        localeResolver.setSupportedLocales(i18nProperties.supportedLocales());
        return localeResolver;
    }
}
