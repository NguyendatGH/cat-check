package com.catcheck.identity.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum IdentityErrorCode implements ErrorCode {


    INVALID_CREDENTIALS("invalid-credentials", HttpStatus.UNAUTHORIZED),

    UNAUTHENTICATED("unauthenticated", HttpStatus.UNAUTHORIZED),

    SESSION_EXPIRED("session-expired", HttpStatus.UNAUTHORIZED),

    SESSION_ABSOLUTE_EXPIRED("session-absolute-expired", HttpStatus.UNAUTHORIZED),

    SESSION_NOT_FOUND("session-not-found", HttpStatus.NOT_FOUND),

    REAUTH_REQUIRED("reauth-required", HttpStatus.FORBIDDEN),

    REAUTH_FAILED("reauth-failed", HttpStatus.UNAUTHORIZED),

    REAUTH_METHOD_UNAVAILABLE("reauth-method-unavailable", HttpStatus.CONFLICT),

    OAUTH_FAILED("oauth-failed", HttpStatus.UNAUTHORIZED),

    OAUTH_EMAIL_UNVERIFIED("oauth-email-unverified", HttpStatus.FORBIDDEN),

    OAUTH_ACCOUNT_LINK_CONFLICT("oauth-account-link-conflict", HttpStatus.CONFLICT),


    EMAIL_ALREADY_REGISTERED("email-already-registered", HttpStatus.CONFLICT),

    ACCOUNT_NOT_VERIFIED("account-not-verified", HttpStatus.FORBIDDEN),

    ACCOUNT_LOCKED("account-locked", HttpStatus.LOCKED),

    ACCOUNT_RESTRICTED("account-restricted", HttpStatus.FORBIDDEN),

    EMAIL_NOT_VERIFIED("email-not-verified", HttpStatus.FORBIDDEN),

    ACCOUNT_DISABLED("account-disabled", HttpStatus.FORBIDDEN),

    ACCOUNT_DELETION_PENDING("account-deletion-pending", HttpStatus.FORBIDDEN),

    CSRF_TOKEN_INVALID("csrf-token-invalid", HttpStatus.FORBIDDEN),

    FORBIDDEN("forbidden", HttpStatus.FORBIDDEN),

    NOT_FOUND("not-found", HttpStatus.NOT_FOUND),


    OTP_INVALID("otp-invalid", HttpStatus.BAD_REQUEST),

    OTP_EXPIRED("otp-expired", HttpStatus.GONE),

    OTP_LOCKED("otp-locked", HttpStatus.TOO_MANY_REQUESTS),

    OTP_RESEND_COOLDOWN("otp-resend-cooldown", HttpStatus.TOO_MANY_REQUESTS),

    OTP_DELIVERY_FAILED("otp-delivery-failed", HttpStatus.SERVICE_UNAVAILABLE),

    RESET_TOKEN_INVALID("reset-token-invalid", HttpStatus.BAD_REQUEST),

    RESET_TOKEN_EXPIRED("reset-token-expired", HttpStatus.GONE),

    OTP_TICKET_INVALID("otp-ticket-invalid", HttpStatus.BAD_REQUEST),


    PASSWORD_TOO_WEAK("password-too-weak", HttpStatus.BAD_REQUEST),

    PASSWORD_BREACHED("password-breached", HttpStatus.BAD_REQUEST),

    PASSWORD_REUSED("password-reused", HttpStatus.CONFLICT),


    SESSION_LIMIT_REACHED("session-limit-reached", HttpStatus.CONFLICT),

    PROFILE_FIELD_NOT_EDITABLE("profile-field-not-editable", HttpStatus.UNPROCESSABLE_ENTITY),

    EMAIL_CHANGE_SAME_ADDRESS("email-change-same-address", HttpStatus.CONFLICT),

    EMAIL_CHANGE_PENDING("email-change-pending", HttpStatus.CONFLICT),

    EMAIL_ALREADY_USED("email-already-used", HttpStatus.CONFLICT),


    TOTP_REQUIRED("totp-required", HttpStatus.FORBIDDEN),

    TOTP_INVALID("totp-invalid", HttpStatus.UNAUTHORIZED),

    TOTP_ALREADY_ENABLED("totp-already-enabled", HttpStatus.CONFLICT),

    TOTP_SETUP_REQUIRED("totp-setup-required", HttpStatus.FORBIDDEN),

    ADMIN_TOTP_REQUIRED("admin-totp-required", HttpStatus.FORBIDDEN),

    ADMIN_CANNOT_MODIFY_SELF("admin-cannot-modify-self", HttpStatus.CONFLICT),

    ROLE_NOT_ASSIGNABLE("role-not-assignable", HttpStatus.CONFLICT),

    MFA_RESET_PENDING("mfa-reset-pending", HttpStatus.CONFLICT),

    MFA_RESET_NOT_PENDING("mfa-reset-not-pending", HttpStatus.CONFLICT),

    MFA_RESET_TWO_PERSON_REQUIRED("mfa-reset-two-person-required", HttpStatus.CONFLICT),

    MFA_RESET_RATE_LIMITED("mfa-reset-rate-limited", HttpStatus.CONFLICT),

    TOTP_LOCKED("totp-locked", HttpStatus.TOO_MANY_REQUESTS),

    TOTP_RECOVERY_INVALID("totp-recovery-invalid", HttpStatus.UNAUTHORIZED),

    TOTP_RECOVERY_EXHAUSTED("totp-recovery-exhausted", HttpStatus.CONFLICT),

    TOTP_NOT_ENABLED("totp-not-enabled", HttpStatus.CONFLICT),

    TOTP_ENROLLMENT_EXPIRED("totp-enrollment-expired", HttpStatus.GONE),

    TOTP_REQUIRED_FOR_ROLE("totp-required-for-role", HttpStatus.CONFLICT),


    IDENTITY_LAST_REMAINING("identity-last-remaining", HttpStatus.CONFLICT),

    IDENTITY_NOT_FOUND("identity-not-found", HttpStatus.NOT_FOUND),

    TIMEZONE_INVALID("timezone-invalid", HttpStatus.BAD_REQUEST),

    AVATAR_INVALID("avatar-invalid", HttpStatus.BAD_REQUEST),

    CONSENT_PURPOSE_UNKNOWN("consent-purpose-unknown", HttpStatus.BAD_REQUEST),


    VALIDATION_FAILED("common/validation-failed", HttpStatus.BAD_REQUEST),

    RATE_LIMITED("rate-limited", HttpStatus.TOO_MANY_REQUESTS),


    PASSWORD_RESET_REQUESTED("password-reset-requested", HttpStatus.OK);

    private final String slug;
    private final HttpStatus status;

    IdentityErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public URI typeUri() {
        return ProblemTypes.of(slug);
    }
}
