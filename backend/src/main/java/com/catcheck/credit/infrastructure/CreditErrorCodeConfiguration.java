package com.catcheck.credit.infrastructure;

import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link CreditErrorCode} vào {@code shared.error.ErrorCodeRegistry}.
 *
 * <p><b>Tại sao không dùng {@code @Bean List<ErrorCode>}</b>: {@code ErrorCodeRegistry} nhận
 * {@code List<ErrorCode>} qua constructor, và Spring gom <b>mọi bean có kiểu {@code ErrorCode}</b>
 * vào danh sách đó. Một {@code @Bean List<ErrorCode>} trả về {@code List}, không phải
 * {@code ErrorCode}, nên <b>không bao giờ</b> được gom — và nếu nhiều module cùng trả về
 * {@code List<ErrorCode>} thì constructor không còn bean định danh duy nhất, context không khởi
 * động được. Vì vậy mỗi hằng enum được khai báo thành <b>một bean {@code ErrorCode} riêng</b>
 * — đúng khuôn của {@code IdentityErrorCodeConfiguration}.</p>
 *
 * <p>Đã kiểm tra trùng mã với {@code content} ({@code VALIDATION_FAILED}, {@code REASON_REQUIRED}),
 * {@code cat} ({@code VALIDATION_FAILED}) và {@code identity}: bảy mã của credit đều duy nhất
 * nên {@code ErrorCodeRegistry.validate} pass.</p>
 */
@Configuration
public class CreditErrorCodeConfiguration {

    @Bean
    ErrorCode creditInsufficient() {
        return CreditErrorCode.CREDIT_INSUFFICIENT;
    }

    @Bean
    ErrorCode activationCodeMalformed() {
        return CreditErrorCode.ACTIVATION_CODE_MALFORMED;
    }

    @Bean
    ErrorCode activationCodeInvalid() {
        return CreditErrorCode.ACTIVATION_CODE_INVALID;
    }

    @Bean
    ErrorCode activationCodeAlreadyUsed() {
        return CreditErrorCode.ACTIVATION_CODE_ALREADY_USED;
    }

    @Bean
    ErrorCode activationCodeExpired() {
        return CreditErrorCode.ACTIVATION_CODE_EXPIRED;
    }

    @Bean
    ErrorCode featureNotInPlan() {
        return CreditErrorCode.FEATURE_NOT_IN_PLAN;
    }

    @Bean
    ErrorCode writeAccessExpired() {
        return CreditErrorCode.WRITE_ACCESS_EXPIRED;
    }

    @Bean
    ErrorCode activationBatchTooLarge() {
        return CreditErrorCode.ACTIVATION_BATCH_TOO_LARGE;
    }

    @Bean
    ErrorCode activationCsvAlreadyDownloaded() {
        return CreditErrorCode.ACTIVATION_CSV_ALREADY_DOWNLOADED;
    }

    @Bean
    ErrorCode activationBatchExists() {
        return CreditErrorCode.ACTIVATION_BATCH_EXISTS;
    }

    @Bean
    ErrorCode activationBatchNotFound() {
        return CreditErrorCode.ACTIVATION_BATCH_NOT_FOUND;
    }

    @Bean
    ErrorCode packagePlanNotFound() {
        return CreditErrorCode.PACKAGE_PLAN_NOT_FOUND;
    }

    @Bean
    ErrorCode creditAdjustLimitExceeded() {
        return CreditErrorCode.CREDIT_ADJUST_LIMIT_EXCEEDED;
    }

    @Bean
    ErrorCode creditAdjustExceedsBalance() {
        return CreditErrorCode.CREDIT_ADJUST_EXCEEDS_BALANCE;
    }
}
