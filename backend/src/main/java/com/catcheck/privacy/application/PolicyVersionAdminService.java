package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.AdminPolicyErrorCode;
import com.catcheck.privacy.domain.PolicyType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.ConsentPurposePort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class PolicyVersionAdminService {

    private static final Pattern SEMVER = Pattern.compile("^\\d{1,4}\\.\\d{1,4}(\\.\\d{1,4})?$");

    private final PolicyVersionPort policyPort;
    private final ConsentPurposePort purposePort;
    private final AuditLogService auditLog;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public PolicyVersionAdminService(PolicyVersionPort policyPort,
                                     ConsentPurposePort purposePort,
                                     AuditLogService auditLog,
                                     UuidV7 uuidV7,
                                     Clock clock) {
        this.policyPort = policyPort;
        this.purposePort = purposePort;
        this.auditLog = auditLog;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    public enum PolicyVersionStatus {
        DRAFT, SCHEDULED, EFFECTIVE, SUPERSEDED
    }

    public record AdminPolicyRow(PolicyVersion version, PolicyVersionStatus status, boolean hasContent) {
    }

    public record AdminPolicyPage(List<AdminPolicyRow> rows, int page, int size,
                                  long totalElements, int totalPages, boolean hasMore) {
    }


    @Transactional(readOnly = true)
    public AdminPolicyPage list(String policyType, String locale, int page, int size) {
        PolicyType type = parseTypeFilter(policyType);
        int cappedSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        long total = policyPort.countForAdmin(type, locale);
        List<PolicyVersion> versions =
                policyPort.findAllForAdmin(type, locale, cappedSize, safePage * cappedSize);
        Instant now = clock.instant();
        List<AdminPolicyRow> rows = versions.stream()
                .map(v -> new AdminPolicyRow(v, statusOf(v, now), v.contentMd() != null))
                .toList();
        int totalPages = (int) Math.ceil((double) total / cappedSize);
        return new AdminPolicyPage(rows, safePage, cappedSize, total, totalPages,
                (long) (safePage + 1) * cappedSize < total);
    }


    @Transactional
    public AdminPolicyRow createDraft(PolicyType type, String version, String locale, String title,
                                      String contentMd, String contentUrl, String summaryOfChanges,
                                      boolean requiresReconsent, List<String> affectedPurposes,
                                      Instant effectiveFrom, PolicyAdminAction action) {
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "version");
        }
        if (contentMd == null && contentUrl == null) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "contentMd");
        }
        Instant now = clock.instant();
        if (effectiveFrom == null || !effectiveFrom.isAfter(now)) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "effectiveFrom");
        }
        if (requiresReconsent && (affectedPurposes == null || affectedPurposes.isEmpty())) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                    "affectedPurposes");
        }
        requirePurposesExist(affectedPurposes);
        if (policyPort.findByTypeAndVersion(type, version, locale).isPresent()) {
            throw new ConflictException(AdminPolicyErrorCode.POLICY_VERSION_EXISTS,
                    type.name(), version, locale);
        }
        String body = contentMd != null ? contentMd : contentUrl;
        PolicyVersion draft = new PolicyVersion(
                uuidV7.generate(), type, version, locale, title, contentMd, contentUrl,
                ContentHashes.sha256Hex(body), summaryOfChanges, requiresReconsent,
                affectedPurposes == null ? List.of() : affectedPurposes,
                effectiveFrom, null, null, now);
        policyPort.publish(draft);
        audit("ADMIN_POLICY_VERSION_DRAFTED", draft, action, Map.of());
        return new AdminPolicyRow(draft, statusOf(draft, now), contentMd != null);
    }


    @Transactional
    public AdminPolicyRow publish(UUID id, Instant effectiveFrom, PolicyAdminAction action) {
        PolicyVersion draft = policyPort.findById(id)
                .orElseThrow(() -> new NotFoundException(
                        AdminPolicyErrorCode.POLICY_VERSION_NOT_FOUND, id.toString()));
        if (draft.publishedBy() != null) {
            throw new ConflictException(
                    AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED, id.toString());
        }
        Instant now = clock.instant();
        Instant effective = effectiveFrom != null ? effectiveFrom : draft.effectiveFrom();
        if (effective == null) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "effectiveFrom");
        }
        int updated = policyPort.markPublished(id, action.actorId(), effective);
        if (updated == 0) {
            throw new ConflictException(
                    AdminPolicyErrorCode.POLICY_VERSION_ALREADY_PUBLISHED, id.toString());
        }
        int closed = policyPort.closeEffective(draft.policyType(), draft.locale(), effective, id);
        audit("ADMIN_POLICY_VERSION_PUBLISHED", draft, action, Map.of(
                "effectiveFrom", effective.toString(),
                "requiresReconsent", draft.requiresReconsent(),
                "affectedPurposes", draft.affectedPurposes(),
                "supersededCount", closed));
        PolicyVersion published = policyPort.findById(id).orElseThrow();
        return new AdminPolicyRow(published, statusOf(published, now), published.contentMd() != null);
    }


    public static PolicyVersionStatus statusOf(PolicyVersion version, Instant now) {
        if (version.effectiveTo() != null && !version.effectiveTo().isAfter(now)) {
            return PolicyVersionStatus.SUPERSEDED;
        }
        if (version.effectiveFrom().isAfter(now)) {
            return version.publishedBy() == null
                    ? PolicyVersionStatus.DRAFT
                    : PolicyVersionStatus.SCHEDULED;
        }
        return PolicyVersionStatus.EFFECTIVE;
    }

    private PolicyType parseTypeFilter(String policyType) {
        if (policyType == null || policyType.isBlank()) {
            return null;
        }
        try {
            return PolicyType.valueOf(policyType.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID, "policyType");
        }
    }

    private void requirePurposesExist(List<String> purposes) {
        if (purposes == null || purposes.isEmpty()) {
            return;
        }
        List<String> known = purposePort.findAllActive().stream()
                .map(com.catcheck.privacy.domain.ConsentPurpose::code)
                .toList();
        for (String purpose : purposes) {
            if (!known.contains(purpose)) {
                throw new BusinessRuleException(AdminPolicyErrorCode.POLICY_VERSION_INVALID,
                        "affectedPurposes." + purpose);
            }
        }
    }

    private void audit(String action, PolicyVersion version, PolicyAdminAction adminAction,
                       Map<String, Object> extra) {
        Map<String, Object> metadata = new LinkedHashMap<>(extra);
        metadata.put("policyVersionId", version.id().toString());
        metadata.put("policyType", version.policyType().name());
        metadata.put("version", version.version());
        metadata.put("locale", version.locale());
        metadata.put("contentHash", version.contentHash());
        metadata.put("reason", adminAction.reason());
        auditLog.record(AuditEvent.builder()
                .actor(AuditActor.dpo(adminAction.actorId(), adminAction.actorRole()))
                .subject(AuditSubjectType.POLICY, null)
                .action(action)
                .metadata(metadata)
                .requestId(adminAction.requestId())
                .ipAddress(adminAction.ipAddress())
                .userAgent(adminAction.userAgent())
                .build());
    }

    public record PolicyAdminAction(UUID actorId, String actorRole, String reason,
                                    String requestId, String ipAddress, String userAgent) {
    }
}
