package com.catcheck.admin.infrastructure;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AdminOpsErrorCodeConfiguration {

    @Bean
    ErrorCode adminSettingKeyUnknown() {
        return AdminOpsErrorCode.SETTING_KEY_UNKNOWN;
    }

    @Bean
    ErrorCode adminJobNotFound() {
        return AdminOpsErrorCode.JOB_NOT_FOUND;
    }

    @Bean
    ErrorCode adminJobNotManuallyRunnable() {
        return AdminOpsErrorCode.JOB_NOT_MANUALLY_RUNNABLE;
    }

    @Bean
    ErrorCode adminJobsDisabled() {
        return AdminOpsErrorCode.JOBS_DISABLED;
    }

    @Bean
    ErrorCode adminOutboxEntryNotFound() {
        return AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FOUND;
    }

    @Bean
    ErrorCode adminOutboxEntryNotFailed() {
        return AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FAILED;
    }

    @Bean
    ErrorCode adminBroadcastTemplateNotAllowed() {
        return AdminOpsErrorCode.BROADCAST_TEMPLATE_NOT_ALLOWED;
    }

    @Bean
    ErrorCode adminBroadcastConfirmationRequired() {
        return AdminOpsErrorCode.BROADCAST_CONFIRMATION_REQUIRED;
    }
}
