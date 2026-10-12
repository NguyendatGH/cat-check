package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFormat;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.credit.domain.ActivationRateLimitRule;
import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.ActivationRateLimiter;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.RateLimitedException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ActivateCreditCodeService {

    private static final Logger log = LoggerFactory.getLogger(ActivateCreditCodeService.class);

    private final ActivationCodePort codePort;

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;
    private final PackagePlanPort packagePlanPort;
    private final ActivationCodeHasher codeHasher;
    private final AuditLogService auditLogService;
    private final EntitlementRecalculationService entitlementRecalculation;
    private final UuidV7 uuidV7;
    private final Clock clock;
    private final ActivationRateLimiter rateLimiter;
    private final ActivationMetrics metrics;

    public ActivateCreditCodeService(
            ActivationCodePort codePort,
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            PackagePlanPort packagePlanPort,
            ActivationCodeHasher codeHasher,
            AuditLogService auditLogService,
            EntitlementRecalculationService entitlementRecalculation,
            UuidV7 uuidV7,
            Clock clock,
            ActivationRateLimiter rateLimiter,
            ActivationMetrics metrics
    ) {
        this.codePort = codePort;
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.packagePlanPort = packagePlanPort;
        this.codeHasher = codeHasher;
        this.auditLogService = auditLogService;
        this.entitlementRecalculation = entitlementRecalculation;
        this.uuidV7 = uuidV7;
        this.clock = clock;
        this.rateLimiter = rateLimiter;
        this.metrics = metrics;
    }

    @Transactional
    public ActivationResult activate(UUID userId, String rawCode, RequestFacts facts) {
        try {
            ActivationResult result = doActivate(userId, rawCode, facts);
            metrics.recordSuccess();
            return result;
        } catch (CatCheckException ex) {
            metrics.recordFailure(ex.errorCode().code());
            throw ex;
        }
    }

    private ActivationResult doActivate(UUID userId, String rawCode, RequestFacts facts) {

        requireRateLimit(ActivationRateLimitRule.ACTIVATION_BY_USER, userId.toString());
        requireRateLimit(ActivationRateLimitRule.ACTIVATION_BY_IP, facts.ipAddress());

        String normalized = ActivationCodeFormat.validateOrNull(rawCode);
        if (normalized == null) {
            throw new BusinessRuleException(
                    CreditErrorCode.ACTIVATION_CODE_MALFORMED, ActivationCodeFormat.expectedFormat());
        }

        ActivationCodeHasher.HashedActivationCode hashed = codeHasher.hash(normalized);
        UUID codeId = codePort.findByCodeHash(hashed.hex())
                .map(ActivationCode::id)
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        Instant now = clock.instant();

        ActivationCode code = codePort.lockById(codeId)
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        if (code.status() == ActivationCodeStatus.REDEEMED) {
            boolean selfRedeem = code.redeemedBy() != null && code.redeemedBy().equals(userId);
            throw new ConflictException(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED,
                    code.redeemedAt(), selfRedeem);
        }
        if (code.status() == ActivationCodeStatus.VOID) {
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID);
        }
        if (code.isIssuanceExpiredAt(now)) {
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_EXPIRED, code.validUntil());
        }

        PackagePlan plan = packagePlanPort.findByCode(code.packageCode())
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        if (!codePort.markRedeemed(code.id(), userId, now)) {
            throw new ConflictException(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED, now, false);
        }

        CreditBatchGrant batch = CreditBatchGrant.of(
                uuidV7.generate(), userId, code.id(), plan, now);
        creditBatchPort.insertNewBatch(batch);

        int balanceAfter = creditLedgerPort.availableBalance(userId, now);
        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(), userId, batch.id(), CreditLedgerType.GRANT, plan.creditAmount(),
                balanceAfter, CreditLedgerRefType.ACTIVATION,
                code.id(), null, plan.name(), now));

        Entitlement entitlement = refreshEntitlement(userId, now);

        log.info("Kích hoạt gói {}: userId={} batchId={} credits={} balanceAfter={}",
                plan.code(), userId, batch.id(), plan.creditAmount(), balanceAfter);

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.user(userId, null))
                .subjectUser(userId)
                .action("CREDIT_ACTIVATION_REDEEMED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("packageCode", plan.code())
                .meta("creditsGranted", plan.creditAmount())
                .meta("batchId", batch.id().toString())
                .requestId(facts.requestId())
                .ipAddress(facts.ipAddress())
                .userAgent(facts.userAgent())
                .build());

        return new ActivationResult(
                plan.code(),
                plan.name(),
                plan.creditAmount(),
                batch.expiresAt(),
                balanceAfter,
                entitlement.maxCatProfiles());
    }

    private void requireRateLimit(ActivationRateLimitRule rule, String subject) {
        ActivationRateLimiter.RetryAfter retryAfter = rateLimiter.rejectIfLimited(rule, subject);
        if (retryAfter != null) {
            throw new RateLimitedException(CreditErrorCode.ACTIVATION_RATE_LIMITED,
                    retryAfter.headerSeconds(), rule.name());
        }
    }

    private Entitlement refreshEntitlement(UUID userId, Instant now) {
        return entitlementRecalculation.recalculate(userId, now);
    }

    public record ActivationResult(
            String packageCode,
            String packageName,
            int creditsGranted,
            Instant expiresAt,
            int balanceAfter,
            Integer maxCatProfiles
    ) {
    }

    public record RequestFacts(String requestId, String ipAddress, String userAgent) {

        public static final RequestFacts UNKNOWN = new RequestFacts(null, null, null);

        public static RequestFacts of(String requestId, String ipAddress, String userAgent) {
            return new RequestFacts(requestId, ipAddress, userAgent);
        }
    }

}
