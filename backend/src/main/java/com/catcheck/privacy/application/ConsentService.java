package com.catcheck.privacy.application;

import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.ConsentMethod;
import com.catcheck.privacy.domain.ConsentPurpose;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsentService {

    private static final String HEALTH_REMINDER_PUSH = "HEALTH_REMINDER_PUSH";

    private final ConsentPurposePort purposePort;
    private final ConsentRecordPort recordPort;
    private final PolicyVersionPort policyPort;
    private final NotificationGateway notificationGateway;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ConsentService(
            ConsentPurposePort purposePort,
            ConsentRecordPort recordPort,
            PolicyVersionPort policyPort,
            NotificationGateway notificationGateway,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.purposePort = purposePort;
        this.recordPort = recordPort;
        this.policyPort = policyPort;
        this.notificationGateway = notificationGateway;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    public List<ConsentPurpose> listPurposes(String locale) {
        return purposePort.findAllActive();
    }

    public List<ConsentCurrentState> listCurrent(UUID userId) {
        List<ConsentCurrentState> states = new ArrayList<>();
        for (ConsentPurpose purpose : purposePort.findAllActive()) {
            ConsentStatus status = recordPort.findLatest(userId, purpose.code())
                    .map(ConsentRecord::status)
                    .orElse(ConsentStatus.NONE);
            states.add(new ConsentCurrentState(purpose.code(), status));
        }
        return states;
    }

    @Transactional
    public List<ConsentCurrentState> recordConsents(
            UUID userId,
            List<ConsentGrant> grants,
            ConsentMethod method,
            String surface,
            String locale,
            RequestEvidence evidence
    ) {
        if (grants == null || grants.isEmpty()) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "consents");
        }
        Instant now = clock.instant();
        PolicyVersion policy = policyPort.findCurrent(PolicyType.PRIVACY, locale, now)
                .orElseThrow(() -> new BusinessRuleException(
                        PrivacyErrorCode.VALIDATION_FAILED, "policyVersion"));

        Map<String, ConsentCurrentState> result = new LinkedHashMap<>();
        for (ConsentGrant grant : grants) {
            ConsentPurpose purpose = purposePort.findByCode(grant.purposeCode())
                    .orElseThrow(() -> new BusinessRuleException(
                            PrivacyErrorCode.CONSENT_PURPOSE_UNKNOWN, grant.purposeCode()));
            ConsentRecord latest = recordPort.findLatest(userId, purpose.code()).orElse(null);
            if (grant.granted()) {
                if (latest == null || latest.status() != ConsentStatus.GRANTED) {
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.GRANTED,
                            latest, policy, method, surface, locale, evidence, now));
                }
            } else {
                if (purpose.isMandatory()) {
                    throw new BusinessRuleException(
                            PrivacyErrorCode.CONSENT_MANDATORY_CANNOT_WITHDRAW,
                            purpose.code(), "DELETE_ACCOUNT");
                }
                if (latest == null) {
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.DENIED,
                            null, policy, method, surface, locale, evidence, now));
                } else if (latest.status() == ConsentStatus.GRANTED) {
                    recordPort.append(buildRecord(userId, purpose, ConsentStatus.WITHDRAWN,
                            latest, policy, method, surface, locale, evidence, now));
                    applyWithdrawEffect(userId, purpose.code());
                }
            }
            result.put(purpose.code(), new ConsentCurrentState(purpose.code(),
                    recordPort.findLatest(userId, purpose.code())
                            .map(ConsentRecord::status)
                            .orElse(ConsentStatus.NONE)));
        }
        return new ArrayList<>(result.values());
    }

    @Transactional
    public void withdrawPurpose(UUID userId, String purposeCode, String surface, String locale, RequestEvidence evidence) {
        ConsentPurpose purpose = purposePort.findByCode(purposeCode)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.CONSENT_PURPOSE_UNKNOWN, purposeCode));
        if (purpose.isMandatory()) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.CONSENT_MANDATORY_CANNOT_WITHDRAW, purposeCode, "DELETE_ACCOUNT");
        }
        ConsentRecord latest = recordPort.findLatest(userId, purposeCode).orElse(null);
        if (latest != null && latest.status() == ConsentStatus.GRANTED) {
            PolicyVersion policy = policyPort.findCurrent(PolicyType.PRIVACY, locale, clock.instant())
                    .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "policyVersion"));
            recordPort.append(buildRecord(userId, purpose, ConsentStatus.WITHDRAWN, latest,
                    policy, ConsentMethod.WEB_TOGGLE, surface, locale, evidence, clock.instant()));
            applyWithdrawEffect(userId, purposeCode);
        }
    }

    private void applyWithdrawEffect(UUID userId, String purposeCode) {
        if (HEALTH_REMINDER_PUSH.equals(purposeCode)) {
            notificationGateway.revokePushOnConsentWithdrawal(userId);
        }
    }

    public List<ConsentRecord> history(UUID userId, int limit) {
        return recordPort.findHistoryByUser(userId, clock.instant(), Math.min(limit, 100));
    }

    private ConsentRecord buildRecord(
            UUID userId,
            ConsentPurpose purpose,
            ConsentStatus status,
            ConsentRecord latest,
            PolicyVersion policy,
            ConsentMethod method,
            String surface,
            String locale,
            RequestEvidence evidence,
            Instant now
    ) {
        String consentText = purpose.labelVy() + "\n" + purpose.descriptionVy();
        return new ConsentRecord(
                uuidV7.generate(),
                userId,
                purpose.code(),
                status,
                policy.id(),
                policy.contentHash(),
                ContentHashes.sha256Hex(consentText),
                method,
                surface,
                locale,
                latest != null ? latest.id() : null,
                now,
                evidence.ipAddress(),
                evidence.userAgent(),
                evidence.requestId(),
                null,
                now);
    }
}
