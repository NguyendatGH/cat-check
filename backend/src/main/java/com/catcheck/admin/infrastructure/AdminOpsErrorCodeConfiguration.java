package com.catcheck.admin.infrastructure;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dang ky {@link AdminOpsErrorCode} vao {@code ErrorCodeRegistry} — moi hang mot bean, cung
 * khuon voi {@code NotificationErrorCodeConfiguration}/{@code CreditErrorCodeConfiguration}.
 *
 * <p>Da grep toan bo {@code src/main/java} truoc khi dang ky: <b>khong hang nao trong enum nay
 * trung {@code code()} voi mot {@code ErrorCode} da dang ky o module khac</b> — ke ca
 * {@code SETTING_KEY_UNKNOWN}, ma p8 §8.2.4(j) da chot nhung chua module nao cai dat. Trung ma
 * se lam {@code ErrorCodeRegistry.validate} nem {@code IllegalStateException} va app khong khoi
 * dong duoc, nen buoc kiem nay la bat buoc, khong phai de thu.</p>
 */
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
}
