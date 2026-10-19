package com.catcheck.credit.infrastructure;

import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    ErrorCode activationRateLimited() {
        return CreditErrorCode.ACTIVATION_RATE_LIMITED;
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
