package com.catcheck.privacy.application;

import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.PolicyAcknowledgment;
import com.catcheck.privacy.domain.PolicySurface;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentRecordPort;
import com.catcheck.privacy.domain.port.PolicyAcknowledgmentPort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PolicyService {

    public static final String ROLE_DPO = "DPO";

    public static final String DEFAULT_LOCALE = "vi";

    private static final List<PolicyType> PUBLIC_TYPES = List.of(
            PolicyType.TERMS, PolicyType.PRIVACY, PolicyType.COOKIE, PolicyType.MEDICAL_DISCLAIMER);

    private final PolicyVersionPort policyPort;
    private final PolicyAcknowledgmentPort acknowledgmentPort;
    private final ConsentRecordPort recordPort;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public PolicyService(
            PolicyVersionPort policyPort,
            PolicyAcknowledgmentPort acknowledgmentPort,
            ConsentRecordPort recordPort,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.policyPort = policyPort;
        this.acknowledgmentPort = acknowledgmentPort;
        this.recordPort = recordPort;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public PolicyVersion publish(
            UUID actor,
            PolicyType type,
            String version,
            String locale,
            String title,
            String contentMd,
            String contentUrl,
            String contentHash,
            String summaryOfChanges,
            boolean requiresReconsent,
            List<String> affectedPurposes,
            Instant effectiveFrom
    ) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(actor);
        if (!snapshot.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        if (requiresReconsent && (affectedPurposes == null || affectedPurposes.isEmpty())) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "affectedPurposes");
        }
        PolicyVersion policy = new PolicyVersion(
                uuidV7.generate(),
                type,
                version,
                locale,
                title,
                contentMd,
                contentUrl,
                contentHash,
                summaryOfChanges,
                requiresReconsent,
                affectedPurposes,
                effectiveFrom,
                null,
                actor,
                clock.instant());
        policyPort.publish(policy);
        return policy;
    }

    public Optional<PolicyVersion> current(PolicyType type, String locale) {
        return policyPort.findCurrent(type, locale, clock.instant());
    }

    public List<PolicyVersion> allVersions(PolicyType type, String locale) {
        List<PolicyVersion> versions = policyPort.findAllByType(type, locale);
        if (versions.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            return policyPort.findAllByType(type, DEFAULT_LOCALE);
        }
        return versions;
    }

    public Optional<PolicyVersion> versionPermalink(PolicyType type, String version, String locale) {
        Optional<PolicyVersion> found = policyPort.findByTypeAndVersion(type, version, locale);
        if (found.isEmpty() && !DEFAULT_LOCALE.equals(locale)) {
            return policyPort.findByTypeAndVersion(type, version, DEFAULT_LOCALE);
        }
        return found;
    }

    public PolicyType parsePublicType(String policyCode) {
        try {
            PolicyType type = PolicyType.valueOf(policyCode);
            if (!PUBLIC_TYPES.contains(type)) {
                throw new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode);
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, policyCode);
        }
    }

    public boolean hasAcknowledged(UUID userId, PolicyType type, PolicySurface surface) {
        Optional<PolicyVersion> current = policyPort.findCurrent(type, "vi", clock.instant());
        if (current.isEmpty()) {
            return true;
        }
        return acknowledgmentPort.exists(userId, current.get().id(), surface);
    }

    @Transactional
    public void acknowledge(UUID userId, PolicyType type, String locale, PolicySurface surface, RequestEvidence evidence) {
        PolicyVersion policy = policyPort.findCurrent(type, locale, clock.instant())
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.POLICY_VERSION_NOT_FOUND, type.name()));
        if (acknowledgmentPort.exists(userId, policy.id(), surface)) {
            return;
        }
        acknowledgmentPort.append(new PolicyAcknowledgment(
                uuidV7.generate(),
                userId,
                policy.id(),
                policy.contentHash(),
                surface,
                clock.instant(),
                evidence.ipAddress(),
                evidence.userAgent(),
                clock.instant()));
    }

    public List<String> pendingReconsentPurposes(UUID userId) {
        Instant now = clock.instant();
        List<PolicyVersion> latestByType = new ArrayList<>();
        for (PolicyType type : List.of(PolicyType.PRIVACY, PolicyType.TERMS)) {
            policyPort.findCurrent(type, "vi", now).ifPresent(latestByType::add);
        }
        List<String> pending = new ArrayList<>();
        for (PolicyVersion policy : latestByType) {
            if (!policy.requiresReconsent()) {
                continue;
            }
            for (String purposeCode : policy.affectedPurposes()) {
                boolean userConsentedUnderThisPolicy = recordPort.findLatest(userId, purposeCode)
                        .map(record -> record.policyVersionId().equals(policy.id()))
                        .orElse(false);
                if (!userConsentedUnderThisPolicy && !pending.contains(purposeCode)) {
                    pending.add(purposeCode);
                }
            }
        }
        return pending;
    }
}
