package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.application.spi.AppSettingPort;
import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

@Service
public class AdminCreditService {

    public static final String KEY_MAX_PER_OPERATION = "credit.admin_adjust_max_per_operation";

    public static final String KEY_MAX_PER_DAY = "credit.admin_adjust_max_per_day";

    public static final int DEFAULT_MAX_PER_OPERATION = 200;

    public static final int DEFAULT_MAX_PER_DAY = 1_000;

    public static final int DEFAULT_PAGE_SIZE = 20;

    public static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(AdminCreditService.class);

    private final CreditBatchPort creditBatchPort;
    private final CreditLedgerPort creditLedgerPort;
    private final CreditLedgerQueryPort creditLedgerQueryPort;
    private final PackagePlanPort packagePlanPort;
    private final AppSettingPort appSettingPort;
    private final EntitlementRecalculationService entitlementRecalculation;
    private final AuditLogService auditLogService;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public AdminCreditService(CreditBatchPort creditBatchPort,
                              CreditLedgerPort creditLedgerPort,
                              CreditLedgerQueryPort creditLedgerQueryPort,
                              PackagePlanPort packagePlanPort,
                              AppSettingPort appSettingPort,
                              EntitlementRecalculationService entitlementRecalculation,
                              AuditLogService auditLogService,
                              UuidV7 uuidV7,
                              Clock clock) {
        this.creditBatchPort = creditBatchPort;
        this.creditLedgerPort = creditLedgerPort;
        this.creditLedgerQueryPort = creditLedgerQueryPort;
        this.packagePlanPort = packagePlanPort;
        this.appSettingPort = appSettingPort;
        this.entitlementRecalculation = entitlementRecalculation;
        this.auditLogService = auditLogService;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }


    @Transactional
    public AdminCreditOverview overview(UUID userId, int page, int size, AdminActionContext context) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        Instant now = clock.instant();

        List<CreditBatchView> batches = creditBatchPort.findAllBatches(userId);
        int availableBalance = creditLedgerPort.availableBalance(userId, now);
        CreditLedgerQueryPort.LedgerOffsetPage ledger =
                creditLedgerQueryPort.findByUserForAdmin(userId, safePage * safeSize, safeSize);

        auditLogService.record(base(context, "ADMIN_USER_CREDITS_VIEW", userId)
                .meta("page", safePage)
                .meta("size", safeSize)
                .meta("batchCount", batches.size())
                .build());

        return new AdminCreditOverview(
                availableBalance, batches, ledger.entries(), ledger.totalElements(), safePage, safeSize);
    }


    @Transactional
    public AdminCreditAdjustmentResult adjust(UUID userId, AdjustmentDirection direction, int amount,
                                              String packageCode, Integer validityDays,
                                              String idempotencyKey, AdminActionContext context) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount phải > 0");
        }

        AdminCreditAdjustmentResult replay = findReplay(idempotencyKey, userId);
        if (replay != null) {
            return replay;
        }

        Instant now = clock.instant();
        requireWithinSafetyLimits(amount, now, context);

        AdminCreditAdjustmentResult result = direction == AdjustmentDirection.GRANT
                ? grant(userId, amount, packageCode, validityDays, idempotencyKey,
                        context.adminId(), now)
                : revokeFefo(userId, amount, idempotencyKey, context.adminId(), now);

        entitlementRecalculation.recalculate(userId, now);

        auditLogService.record(base(context, "CREDIT_ADJUST", userId)
                .meta("direction", direction.name())
                .meta("amount", amount)
                .meta("batchId", result.batchId())
                .before(Map.of("availableBalance", result.balanceBefore()))
                .after(Map.of("availableBalance", result.balanceAfter()))
                .build());

        log.info("Điều chỉnh credit {} {} cho userId={} bởi adminId={} (số dư {} -> {})",
                direction, amount, userId, context.adminId(),
                result.balanceBefore(), result.balanceAfter());
        return result;
    }


    private AdminCreditAdjustmentResult grant(UUID userId, int amount, String packageCode,
                                               Integer validityDays, String idempotencyKey,
                                               UUID adminId, Instant now) {
        PackagePlan plan = packagePlanPort.findByCode(packageCode)
                .orElseThrow(() -> new NotFoundException(
                        CreditErrorCode.PACKAGE_PLAN_NOT_FOUND, packageCode));

        int days = validityDays == null ? plan.creditValidityDays() : validityDays;
        if (days <= 0) {
            throw new IllegalArgumentException("validityDays phải > 0");
        }

        int balanceBefore = creditLedgerPort.availableBalance(userId, now);

        CreditBatchGrant batch = new CreditBatchGrant(
                uuidV7.generate(), userId, null, plan.code(), plan.version(), amount,
                now, now.plus(Duration.ofDays(days)));
        creditBatchPort.insertNewBatch(batch);

        int balanceAfter = creditLedgerPort.availableBalance(userId, now);
        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(), userId, batch.id(), CreditLedgerType.GRANT, amount,
                balanceAfter, CreditLedgerRefType.ADMIN, adminId, idempotencyKey,
                adjustmentNote(), now));

        return new AdminCreditAdjustmentResult(
                AdjustmentDirection.GRANT, amount, balanceBefore, balanceAfter,
                batch.id(), batch.expiresAt(), List.of(new BatchDelta(batch.id(), amount)));
    }

    private AdminCreditAdjustmentResult revokeFefo(UUID userId, int amount, String idempotencyKey,
                                                    UUID adminId, Instant now) {
        List<CreditBatchSnapshot> candidates = creditLedgerPort.lockLiveBatchesForFefo(userId, now);
        int available = candidates.stream().mapToInt(CreditBatchSnapshot::remainingAmount).sum();
        if (available < amount) {
            throw new ConflictException(
                    CreditErrorCode.CREDIT_ADJUST_EXCEEDS_BALANCE, available, amount);
        }

        Map<UUID, Integer> taken = new LinkedHashMap<>();
        int needed = amount;
        for (CreditBatchSnapshot batch : candidates) {
            if (needed == 0) {
                break;
            }
            int take = Math.min(needed, batch.remainingAmount());
            if (take <= 0) {
                continue;
            }
            int remaining = batch.remainingAmount() - take;
            creditLedgerPort.updateRemaining(batch.id(), remaining);
            if (remaining == 0) {
                creditLedgerPort.markExhausted(batch.id());
            }
            taken.put(batch.id(), take);
            needed -= take;
        }

        int balanceAfter = creditLedgerPort.availableBalance(userId, now);

        boolean firstRow = true;
        List<BatchDelta> deltas = new ArrayList<>(taken.size());
        for (Map.Entry<UUID, Integer> entry : taken.entrySet()) {
            creditLedgerPort.append(new LedgerEntry(
                    uuidV7.generate(), userId, entry.getKey(), CreditLedgerType.ADJUST,
                    -entry.getValue(), balanceAfter, CreditLedgerRefType.ADMIN, adminId,
                    firstRow ? idempotencyKey : null, adjustmentNote(), now));
            deltas.add(new BatchDelta(entry.getKey(), -entry.getValue()));
            firstRow = false;
        }

        return new AdminCreditAdjustmentResult(
                AdjustmentDirection.REVOKE, amount, available, balanceAfter, null, null, deltas);
    }

    private void requireWithinSafetyLimits(int amount, Instant now, AdminActionContext context) {
        int maxPerOperation = setting(KEY_MAX_PER_OPERATION, DEFAULT_MAX_PER_OPERATION);
        int maxPerDay = setting(KEY_MAX_PER_DAY, DEFAULT_MAX_PER_DAY);

        Instant startOfDay = now.truncatedTo(ChronoUnit.DAYS);
        int usedToday = creditLedgerPort.sumAdminAdjustedAbsSince(context.adminId(), startOfDay);
        int remainingToday = Math.max(0, maxPerDay - usedToday);

        if (amount > maxPerOperation || amount > remainingToday) {
            throw new BusinessRuleException(CreditErrorCode.CREDIT_ADJUST_LIMIT_EXCEEDED,
                    maxPerOperation, maxPerDay, remainingToday);
        }
    }

    private int setting(String key, int fallback) {
        OptionalInt value = appSettingPort.findInt(key);
        return value.isPresent() ? value.getAsInt() : fallback;
    }

    private AdminCreditAdjustmentResult findReplay(String idempotencyKey, UUID userId) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return creditLedgerPort.findByIdempotencyKey(idempotencyKey)
                .filter(entry -> entry.userId().equals(userId))
                .map(entry -> new AdminCreditAdjustmentResult(
                        entry.amount() > 0 ? AdjustmentDirection.GRANT : AdjustmentDirection.REVOKE,
                        Math.abs(entry.amount()),
                        entry.balanceAfter() - entry.amount(),
                        entry.balanceAfter(),
                        entry.batchId(),
                        null,
                        List.of(new BatchDelta(entry.batchId(), entry.amount())),
                        true))
                .orElse(null);
    }

    private static String adjustmentNote() {
        return "Điều chỉnh bởi CatCheck";
    }

    private AuditEvent.Builder base(AdminActionContext context, String action, UUID subjectUserId) {
        return AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.USER, subjectUserId)
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .meta("reason", context.reason())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent());
    }

    public enum AdjustmentDirection {

        GRANT,

        REVOKE
    }

    public record AdminCreditOverview(
            int availableBalance,
            List<CreditBatchView> batches,
            List<CreditLedgerQueryPort.LedgerRow> ledger,
            long ledgerTotal,
            int ledgerPage,
            int ledgerSize
    ) {

        public AdminCreditOverview {
            batches = batches == null ? List.of() : List.copyOf(batches);
            ledger = ledger == null ? List.of() : List.copyOf(ledger);
        }
    }

    public record BatchDelta(UUID batchId, int amount) {
    }

    public record AdminCreditAdjustmentResult(
            AdjustmentDirection direction,
            int amount,
            int balanceBefore,
            int balanceAfter,
            UUID batchId,
            Instant expiresAt,
            List<BatchDelta> batches,
            boolean replayed
    ) {

        public AdminCreditAdjustmentResult {
            batches = batches == null ? List.of() : List.copyOf(batches);
        }

        AdminCreditAdjustmentResult(AdjustmentDirection direction, int amount, int balanceBefore,
                                     int balanceAfter, UUID batchId, Instant expiresAt,
                                     List<BatchDelta> batches) {
            this(direction, amount, balanceBefore, balanceAfter, batchId, expiresAt, batches, false);
        }
    }
}
