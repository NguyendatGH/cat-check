package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.credit.api.BuyerAccountActivatedEvent;
import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.WelcomeBonus;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tặng quà chào mừng (mặc định 10 credit, hạn 5 ngày) đúng MỘT lần cho mỗi tài khoản người mua.
 *
 * <p>Idempotent hai lớp: (1) khoá dòng {@code user_entitlement} FOR UPDATE rồi tra
 * {@code credit_ledger.idempotency_key = 'welcome-bonus:<userId>'}; (2) chính cột đó UNIQUE ở DB.
 * Ledger dùng type GRANT, ref_type NULL — cả hai đã được CHECK của V10 cho phép, không cần migration.
 */
@Service
public class WelcomeBonusService {

    private static final Logger log = LoggerFactory.getLogger(WelcomeBonusService.class);

    private final WelcomeBonusProperties properties;
    private final CreditBatchPort creditBatchPort;
    private final CreditLedgerPort creditLedgerPort;
    private final UserEntitlementPort userEntitlementPort;
    private final EntitlementRecalculationService entitlementRecalculation;
    private final AuditLogService auditLogService;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public WelcomeBonusService(WelcomeBonusProperties properties,
                               CreditBatchPort creditBatchPort,
                               CreditLedgerPort creditLedgerPort,
                               UserEntitlementPort userEntitlementPort,
                               EntitlementRecalculationService entitlementRecalculation,
                               AuditLogService auditLogService,
                               UuidV7 uuidV7,
                               Clock clock) {
        this.properties = properties;
        this.creditBatchPort = creditBatchPort;
        this.creditLedgerPort = creditLedgerPort;
        this.userEntitlementPort = userEntitlementPort;
        this.entitlementRecalculation = entitlementRecalculation;
        this.auditLogService = auditLogService;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @EventListener
    @Transactional
    public void onBuyerAccountActivated(BuyerAccountActivatedEvent event) {
        grant(event.userId(), event.method().name());
    }

    /**
     * @return lô vừa tạo, hoặc rỗng nếu tính năng tắt / user đã từng nhận quà.
     */
    @Transactional
    public Optional<CreditBatchGrant> grant(UUID userId, String source) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        userEntitlementPort.lockRow(userId, now);

        String idempotencyKey = WelcomeBonus.idempotencyKey(userId);
        if (creditLedgerPort.findByIdempotencyKey(idempotencyKey).isPresent()) {
            return Optional.empty();
        }

        CreditBatchGrant batch = new CreditBatchGrant(
                uuidV7.generate(), userId, null,
                WelcomeBonus.PACKAGE_CODE, WelcomeBonus.PACKAGE_VERSION,
                properties.amount(), now, now.plus(properties.validity()));
        creditBatchPort.insertNewBatch(batch);

        int balanceAfter = creditLedgerPort.availableBalance(userId, now);
        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(), userId, batch.id(), CreditLedgerType.GRANT, batch.creditAmount(),
                balanceAfter, null, null, idempotencyKey, WelcomeBonus.LEDGER_NOTE, now));

        entitlementRecalculation.recalculate(userId, now);

        log.info("Tặng quà chào mừng: userId={} batchId={} credits={} source={}",
                userId, batch.id(), batch.creditAmount(), source);

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.system())
                .subjectUser(userId)
                .action("CREDIT_WELCOME_BONUS_GRANTED")
                .outcome(AuditOutcome.SUCCESS)
                .meta("creditsGranted", batch.creditAmount())
                .meta("batchId", batch.id().toString())
                .meta("source", source)
                .build());

        return Optional.of(batch);
    }
}
