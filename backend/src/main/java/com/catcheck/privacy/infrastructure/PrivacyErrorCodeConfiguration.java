package com.catcheck.privacy.infrastructure;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link PrivacyErrorCode} vào {@code shared.error.ErrorCodeRegistry}.
 *
 * <p><b>Tại sao không dùng {@code @Bean List<ErrorCode>}</b>: {@code ErrorCodeRegistry} nhận
 * {@code List<ErrorCode>} qua constructor, và Spring gom <b>mọi bean có kiểu {@code ErrorCode}</b>
 * vào danh sách đó. Một {@code @Bean List<ErrorCode>} trả về {@code List}, không phải
 * {@code ErrorCode}, nên <b>không bao giờ</b> được gom — và nếu nhiều module cùng trả về
 * {@code List<ErrorCode>} thì constructor không còn bean định danh duy nhất, context không
 * khởi động được. Vì vậy mỗi hằng enum được khai báo thành <b>một bean {@code ErrorCode}
 * riêng</b> — đúng khuôn của {@code CreditErrorCodeConfiguration} và
 * {@code IdentityErrorCodeConfiguration}.</p>
 *
 * <p>Chỉ 14 hằng nhóm (c)/(i) được đăng ký. Bốn hằng dùng chung ({@code VALIDATION_FAILED},
 * {@code FORBIDDEN}, {@code RATE_LIMITED}, {@code CONSENT_PURPOSE_UNKNOWN}) và
 * {@code AVATAR_INVALID} cố ý <b>không có bean</b>: chúng đã thuộc nhóm (a)/(b)/(c) của
 * p8 và đã được module khác khai báo — đăng ký nữa sẽ làm {@code ErrorCodeRegistry.validate}
 * ném {@code IllegalStateException} khi khởi động. Chúng vẫn nằm trong enum để service
 * <b>ném</b> được. Xem javadoc {@link PrivacyErrorCode} và {@code docs/handovers/A2.md}.</p>
 */
@Configuration
public class PrivacyErrorCodeConfiguration {

    @Bean
    ErrorCode consentMandatoryCannotWithdraw() {
        return PrivacyErrorCode.CONSENT_MANDATORY_CANNOT_WITHDRAW;
    }

    @Bean
    ErrorCode consentRequired() {
        return PrivacyErrorCode.CONSENT_REQUIRED;
    }

    @Bean
    ErrorCode policyReacceptRequired() {
        return PrivacyErrorCode.POLICY_REACCEPT_REQUIRED;
    }

    @Bean
    ErrorCode policyVersionNotFound() {
        return PrivacyErrorCode.POLICY_VERSION_NOT_FOUND;
    }

    @Bean
    ErrorCode dsarNotFound() {
        return PrivacyErrorCode.DSAR_NOT_FOUND;
    }

    @Bean
    ErrorCode retentionPolicyNotFound() {
        return PrivacyErrorCode.RETENTION_POLICY_NOT_FOUND;
    }

    @Bean
    ErrorCode dsarIdentityVerificationRequired() {
        return PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED;
    }

    @Bean
    ErrorCode dsarExportRateLimited() {
        return PrivacyErrorCode.DSAR_EXPORT_RATE_LIMITED;
    }

    @Bean
    ErrorCode dsarExportNotReady() {
        return PrivacyErrorCode.DSAR_EXPORT_NOT_READY;
    }

    @Bean
    ErrorCode dsarExportExpired() {
        return PrivacyErrorCode.DSAR_EXPORT_EXPIRED;
    }

    @Bean
    ErrorCode dsarExportAlreadyDownloaded() {
        return PrivacyErrorCode.DSAR_EXPORT_ALREADY_DOWNLOADED;
    }

    @Bean
    ErrorCode deletionAlreadyRequested() {
        return PrivacyErrorCode.DELETION_ALREADY_REQUESTED;
    }

    @Bean
    ErrorCode deletionGraceExpired() {
        return PrivacyErrorCode.DELETION_GRACE_EXPIRED;
    }

    @Bean
    ErrorCode deletionNotRequested() {
        return PrivacyErrorCode.DELETION_NOT_REQUESTED;
    }

    @Bean
    ErrorCode dsarRequestTypeUnsupported() {
        return PrivacyErrorCode.DSAR_REQUEST_TYPE_UNSUPPORTED;
    }

    @Bean
    ErrorCode restrictionAlreadyActive() {
        return PrivacyErrorCode.RESTRICTION_ALREADY_ACTIVE;
    }
}
