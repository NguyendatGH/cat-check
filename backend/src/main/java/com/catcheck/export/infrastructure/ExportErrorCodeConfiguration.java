package com.catcheck.export.infrastructure;

import com.catcheck.export.api.ExportErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link ExportErrorCode} vào {@code ErrorCodeRegistry} — mỗi hằng một
 * {@code @Bean ErrorCode} riêng. Đã kiểm tra không trùng với scan/insight/identity/privacy/
 * cat/credit/colorchart.
 *
 * <p><b>Một hằng CỐ Ý không đăng ký:</b> {@code FEATURE_NOT_IN_PLAN} —
 * {@code CreditErrorCodeConfiguration.featureNotInPlan()} đã đăng ký (cùng quy ước ở
 * {@code ReminderErrorCodeConfiguration}/{@code CatErrorCodeConfiguration}). Đăng ký lần nữa sẽ
 * làm context sập lúc khởi động.</p>
 */
@Configuration
public class ExportErrorCodeConfiguration {

    @Bean
    ErrorCode exportJobInProgress() {
        return ExportErrorCode.EXPORT_JOB_IN_PROGRESS;
    }

    @Bean
    ErrorCode exportNoData() {
        return ExportErrorCode.EXPORT_NO_DATA;
    }

    @Bean
    ErrorCode exportJobNotFound() {
        return ExportErrorCode.EXPORT_JOB_NOT_FOUND;
    }

    @Bean
    ErrorCode exportNotReady() {
        return ExportErrorCode.EXPORT_NOT_READY;
    }

    @Bean
    ErrorCode exportExpired() {
        return ExportErrorCode.EXPORT_EXPIRED;
    }

    @Bean
    ErrorCode exportRangeInvalid() {
        return ExportErrorCode.EXPORT_RANGE_INVALID;
    }
}
