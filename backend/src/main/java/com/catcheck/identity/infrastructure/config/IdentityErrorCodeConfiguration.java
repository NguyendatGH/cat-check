package com.catcheck.identity.infrastructure.config;

import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Dang ky {@link IdentityErrorCode} vao {@code shared.error.ErrorCodeRegistry}.
 *
 * <p><b>Tai sao khong the dung {@code @Bean List<ErrorCode>}</b>: {@code ErrorCodeRegistry}
 * nhan {@code List<ErrorCode>} qua constructor, va Spring gom <b>moi bean co kieu
 * {@code ErrorCode}</b> vao danh sach do. Mot {@code @Bean List<ErrorCode>} tra ve
 * {@code List}, khong phai {@code ErrorCode}, nen <b>khong bao gio</b> duoc gom — va neu
 * nhieu module cung tra bien do thi con khong con bean dinh danh duy nhat. Do vay moi
 * module dang ky <b>tung hang</b> {@link IdentityErrorCode} nhu mot bean {@code ErrorCode}
 * ({@code ErrorCodeRegistry} se tu gop tat ca).</p>
 *
 * <p>Doi voi {@code credit}/{@code content}/{@code media} (A2/A3/A4): ho chua dang ky
 * nen registry hien chi thay ma cua identity. Cac module do chua co trung ma nen
 * registry van pass. Xem {@code docs/handovers/A1.md}.</p>
 */
@Configuration
public class IdentityErrorCodeConfiguration {

    @Bean
    ErrorCode invalidCredentials() {
        return IdentityErrorCode.INVALID_CREDENTIALS;
    }

    @Bean
    ErrorCode unauthenticated() {
        return IdentityErrorCode.UNAUTHENTICATED;
    }

    @Bean
    ErrorCode sessionExpired() {
        return IdentityErrorCode.SESSION_EXPIRED;
    }

    @Bean
    ErrorCode sessionAbsoluteExpired() {
        return IdentityErrorCode.SESSION_ABSOLUTE_EXPIRED;
    }

    @Bean
    ErrorCode sessionNotFound() {
        return IdentityErrorCode.SESSION_NOT_FOUND;
    }

    @Bean
    ErrorCode reauthRequired() {
        return IdentityErrorCode.REAUTH_REQUIRED;
    }

    @Bean
    ErrorCode reauthFailed() {
        return IdentityErrorCode.REAUTH_FAILED;
    }

    @Bean
    ErrorCode reauthMethodUnavailable() {
        return IdentityErrorCode.REAUTH_METHOD_UNAVAILABLE;
    }

    @Bean
    ErrorCode oauthFailed() {
        return IdentityErrorCode.OAUTH_FAILED;
    }

    @Bean
    ErrorCode oauthEmailUnverified() {
        return IdentityErrorCode.OAUTH_EMAIL_UNVERIFIED;
    }

    @Bean
    ErrorCode oauthAccountLinkConflict() {
        return IdentityErrorCode.OAUTH_ACCOUNT_LINK_CONFLICT;
    }

    @Bean
    ErrorCode emailAlreadyRegistered() {
        return IdentityErrorCode.EMAIL_ALREADY_REGISTERED;
    }

    @Bean
    ErrorCode accountNotVerified() {
        return IdentityErrorCode.ACCOUNT_NOT_VERIFIED;
    }

    @Bean
    ErrorCode accountLocked() {
        return IdentityErrorCode.ACCOUNT_LOCKED;
    }

    @Bean
    ErrorCode accountRestricted() {
        return IdentityErrorCode.ACCOUNT_RESTRICTED;
    }

    @Bean
    ErrorCode otpInvalid() {
        return IdentityErrorCode.OTP_INVALID;
    }

    @Bean
    ErrorCode otpExpired() {
        return IdentityErrorCode.OTP_EXPIRED;
    }

    @Bean
    ErrorCode otpLocked() {
        return IdentityErrorCode.OTP_LOCKED;
    }

    @Bean
    ErrorCode otpResendCooldown() {
        return IdentityErrorCode.OTP_RESEND_COOLDOWN;
    }

    @Bean
    ErrorCode otpDeliveryFailed() {
        return IdentityErrorCode.OTP_DELIVERY_FAILED;
    }

    @Bean
    ErrorCode otpTicketInvalid() {
        return IdentityErrorCode.OTP_TICKET_INVALID;
    }

    @Bean
    ErrorCode passwordTooWeak() {
        return IdentityErrorCode.PASSWORD_TOO_WEAK;
    }

    @Bean
    ErrorCode passwordBreached() {
        return IdentityErrorCode.PASSWORD_BREACHED;
    }

    @Bean
    ErrorCode passwordReused() {
        return IdentityErrorCode.PASSWORD_REUSED;
    }

    @Bean
    ErrorCode sessionLimitReached() {
        return IdentityErrorCode.SESSION_LIMIT_REACHED;
    }

    @Bean
    ErrorCode profileFieldNotEditable() {
        return IdentityErrorCode.PROFILE_FIELD_NOT_EDITABLE;
    }

    @Bean
    ErrorCode emailChangeSameAddress() {
        return IdentityErrorCode.EMAIL_CHANGE_SAME_ADDRESS;
    }

    @Bean
    ErrorCode emailChangePending() {
        return IdentityErrorCode.EMAIL_CHANGE_PENDING;
    }

    @Bean
    ErrorCode emailAlreadyUsed() {
        return IdentityErrorCode.EMAIL_ALREADY_USED;
    }

    @Bean
    ErrorCode rateLimited() {
        return IdentityErrorCode.RATE_LIMITED;
    }

    @Bean
    ErrorCode passwordResetRequested() {
        return IdentityErrorCode.PASSWORD_RESET_REQUESTED;
    }

    @Bean
    ErrorCode emailNotVerified() {
        return IdentityErrorCode.EMAIL_NOT_VERIFIED;
    }

    @Bean
    ErrorCode accountDisabled() {
        return IdentityErrorCode.ACCOUNT_DISABLED;
    }

    @Bean
    ErrorCode accountDeletionPending() {
        return IdentityErrorCode.ACCOUNT_DELETION_PENDING;
    }

    @Bean
    ErrorCode csrfTokenInvalid() {
        return IdentityErrorCode.CSRF_TOKEN_INVALID;
    }

    @Bean
    ErrorCode forbidden() {
        return IdentityErrorCode.FORBIDDEN;
    }

    @Bean
    ErrorCode notFound() {
        return IdentityErrorCode.NOT_FOUND;
    }

    @Bean
    ErrorCode resetTokenInvalid() {
        return IdentityErrorCode.RESET_TOKEN_INVALID;
    }

    @Bean
    ErrorCode resetTokenExpired() {
        return IdentityErrorCode.RESET_TOKEN_EXPIRED;
    }

    @Bean
    ErrorCode totpRequired() {
        return IdentityErrorCode.TOTP_REQUIRED;
    }

    @Bean
    ErrorCode totpInvalid() {
        return IdentityErrorCode.TOTP_INVALID;
    }

    @Bean
    ErrorCode totpAlreadyEnabled() {
        return IdentityErrorCode.TOTP_ALREADY_ENABLED;
    }

    @Bean
    ErrorCode totpSetupRequired() {
        return IdentityErrorCode.TOTP_SETUP_REQUIRED;
    }

    @Bean
    ErrorCode totpLocked() {
        return IdentityErrorCode.TOTP_LOCKED;
    }

    @Bean
    ErrorCode totpRecoveryInvalid() {
        return IdentityErrorCode.TOTP_RECOVERY_INVALID;
    }

    @Bean
    ErrorCode totpRecoveryExhausted() {
        return IdentityErrorCode.TOTP_RECOVERY_EXHAUSTED;
    }

    @Bean
    ErrorCode totpNotEnabled() {
        return IdentityErrorCode.TOTP_NOT_ENABLED;
    }

    @Bean
    ErrorCode totpEnrollmentExpired() {
        return IdentityErrorCode.TOTP_ENROLLMENT_EXPIRED;
    }

    @Bean
    ErrorCode totpRequiredForRole() {
        return IdentityErrorCode.TOTP_REQUIRED_FOR_ROLE;
    }

    @Bean
    ErrorCode identityLastRemaining() {
        return IdentityErrorCode.IDENTITY_LAST_REMAINING;
    }

    @Bean
    ErrorCode identityNotFound() {
        return IdentityErrorCode.IDENTITY_NOT_FOUND;
    }

    @Bean
    ErrorCode timezoneInvalid() {
        return IdentityErrorCode.TIMEZONE_INVALID;
    }

    @Bean
    ErrorCode avatarInvalid() {
        return IdentityErrorCode.AVATAR_INVALID;
    }

    @Bean
    ErrorCode consentPurposeUnknown() {
        return IdentityErrorCode.CONSENT_PURPOSE_UNKNOWN;
    }
}
