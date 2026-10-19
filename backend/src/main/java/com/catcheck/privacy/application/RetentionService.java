package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.privacy.domain.port.RetentionDryRunPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

@Service
public class RetentionService {

    public static final int SCAN_IMAGE_MAX_DAYS = 14;

    public static final int ACCOUNT_DELETION_GRACE_MAX_DAYS = 7;

    private static final List<String> SCAN_IMAGE_CODE_PREFIXES = List.of("SCAN_IMAGE");
    private static final List<String> DELETION_GRACE_CODE_PREFIXES =
            List.of("ACCOUNT_DELETION", "ACCOUNT_ERASE", "DELETION_GRACE");

    private static final String ROLE_DPO = "DPO";

    private final RetentionPolicyPort policyPort;
    private final UserAccountPort userAccountPort;
    private final RetentionDryRunPort dryRunPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public RetentionService(
            RetentionPolicyPort policyPort,
            UserAccountPort userAccountPort,
            RetentionDryRunPort dryRunPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.policyPort = policyPort;
        this.userAccountPort = userAccountPort;
        this.dryRunPort = dryRunPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    public List<RetentionPolicy> listPolicies() {
        return policyPort.findAll();
    }

    public void savePolicy(RetentionPolicy policy, UUID actor) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(actor);
        if (!snapshot.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        requireWithinHardCap(policy);
        policyPort.save(policy);
    }

    private static void requireWithinHardCap(RetentionPolicy policy) {
        if (policy.retentionDays() == null) {
            return;
        }
        Integer cap = hardCapFor(policy.code());
        if (cap != null && policy.retentionDays() > cap) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED, policy.code(), cap);
        }
    }

    public static Integer hardCapFor(String code) {
        if (code == null) {
            return null;
        }
        String normalised = code.strip().toUpperCase(java.util.Locale.ROOT);
        if (SCAN_IMAGE_CODE_PREFIXES.stream().anyMatch(normalised::startsWith)) {
            return SCAN_IMAGE_MAX_DAYS;
        }
        return DELETION_GRACE_CODE_PREFIXES.stream().anyMatch(normalised::startsWith)
                ? ACCOUNT_DELETION_GRACE_MAX_DAYS
                : null;
    }

    public DryRunResult dryRun(String code, UUID actor, String actorRole, RequestEvidence evidence) {
        RetentionPolicy policy = policyPort.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.RETENTION_POLICY_NOT_FOUND, code));
        Instant now = clock.instant();
        Instant cutoff = policy.retentionDays() == null
                ? now
                : now.minusSeconds(policy.retentionDays() * 86_400L);
        long candidateCount = policy.retentionDays() == null
                ? 0
                : dryRunPort.countExpired(policy, cutoff);
        auditLogService.record(AuditEvent.builder()
                .actor("DPO".equals(actorRole) ? AuditActor.dpo(actor, actorRole) : AuditActor.admin(actor, actorRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("RETENTION_DRY_RUN")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("policyCode", policy.code())
                .meta("targetTable", policy.targetTable())
                .meta("candidateCount", candidateCount)
                .meta("cutoff", cutoff)
                .build());
        return new DryRunResult(policy.code(), policy.targetTable(), policy.anchorColumn(),
                policy.retentionDays(), cutoff, candidateCount, policy.actionOnExpiry().name());
    }

    public record DryRunResult(
            String policyCode,
            String targetTable,
            String anchorColumn,
            Integer retentionDays,
            Instant cutoff,
            long candidateCount,
            String actionOnExpiry) {
    }
}
