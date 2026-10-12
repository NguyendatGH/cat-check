package com.catcheck.privacy.infrastructure;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    @Bean
    ErrorCode dsarSelfApprovalForbidden() {
        return PrivacyErrorCode.DSAR_SELF_APPROVAL_FORBIDDEN;
    }

    @Bean
    ErrorCode retentionLimitExceeded() {
        return PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED;
    }

    @Bean
    ErrorCode securityIncidentNotFound() {
        return PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND;
    }
}
