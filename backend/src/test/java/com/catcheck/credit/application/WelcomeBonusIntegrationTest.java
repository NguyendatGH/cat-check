package com.catcheck.credit.application;

import com.catcheck.credit.api.BuyerAccountActivatedEvent;
import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.application.AdminActivationTestDoubles.RecordingAuditLog;
import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.CreditBalance;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.WelcomeBonus;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.credit.infrastructure.crypto.ActivationPepperProperties;
import com.catcheck.credit.infrastructure.crypto.HmacActivationCodeHasher;
import com.catcheck.credit.infrastructure.persistence.JdbcActivationCodeAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcCreditBatchAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcCreditLedgerAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcCreditLedgerQueryAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcPackagePlanAdapter;
import com.catcheck.credit.infrastructure.persistence.JdbcUserEntitlementAdapter;
import com.catcheck.credit.infrastructure.ratelimit.Bucket4jActivationRateLimiter;
import com.catcheck.shared.id.UuidV7;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy trên Postgres local (giống ActivationCreditEndToEndIntegrationTest) để CHECK/UNIQUE của V10 được ép thật. */
class WelcomeBonusIntegrationTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    private JdbcTemplate jdbc;
    private MutableClock clock;
    private CreditBatchPort batchPort;
    private CreditLedgerPort ledgerPort;
    private CreditLedgerQueryPort ledgerQueryPort;
    private UserEntitlementPort entitlementPort;
    private PackagePlanPort planPort;
    private EntitlementRecalculationService recalculation;
    private RecordingAuditLog auditLog;
    private UuidV7 uuidV7;
    private UUID userId;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD));
        clock = new MutableClock(Instant.parse("2026-03-01T00:00:00Z"));
        JsonMapper jsonMapper = JsonMapper.builder().build();
        batchPort = new JdbcCreditBatchAdapter(jdbc);
        ledgerPort = new JdbcCreditLedgerAdapter(jdbc);
        ledgerQueryPort = new JdbcCreditLedgerQueryAdapter(jdbc);
        entitlementPort = new JdbcUserEntitlementAdapter(jdbc, jsonMapper);
        planPort = new JdbcPackagePlanAdapter(jdbc, jsonMapper);
        recalculation = new EntitlementRecalculationService(entitlementPort, batchPort, planPort);
        auditLog = new RecordingAuditLog();
        uuidV7 = new UuidV7(clock);
        userId = insertTestUser();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM credit_batch WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM activation_code WHERE redeemed_by = ?", userId);
        jdbc.update("DELETE FROM user_entitlement WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    void grantsTenCreditsForFiveDaysExactlyOnce() {
        WelcomeBonusService service = service(new WelcomeBonusProperties(true, 10, Duration.ofDays(5)));
        Instant grantedAt = clock.instant();

        service.onBuyerAccountActivated(new BuyerAccountActivatedEvent(
                userId, BuyerAccountActivatedEvent.SignupMethod.EMAIL, grantedAt));
        Optional<CreditBatchGrant> again = service.grant(userId, "GOOGLE");
        service.onBuyerAccountActivated(new BuyerAccountActivatedEvent(
                userId, BuyerAccountActivatedEvent.SignupMethod.GOOGLE, grantedAt));

        assertThat(again).isEmpty();
        List<CreditBatchView> batches = batchPort.findAllBatches(userId);
        assertThat(batches).hasSize(1);
        CreditBatchView batch = batches.getFirst();
        assertThat(batch.packageCode()).isEqualTo(WelcomeBonus.PACKAGE_CODE);
        assertThat(batch.initialAmount()).isEqualTo(10);
        assertThat(batch.remainingAmount()).isEqualTo(10);
        assertThat(batch.expiresAt()).isEqualTo(grantedAt.plus(Duration.ofDays(5)));
        assertThat(ledgerQueryPort.findByUser(userId, null, 20).entries()).hasSize(1);
        assertThat(ledgerPort.findByIdempotencyKey(WelcomeBonus.idempotencyKey(userId))).isPresent();
        assertThat(auditLog.actions()).containsExactly("CREDIT_WELCOME_BONUS_GRANTED");

        CreditBalance balance = new CreditBalanceService(batchPort, entitlementPort, clock).balanceOf(userId);
        assertThat(balance.availableBalance()).isEqualTo(10);
        assertThat(balance.batches()).extracting(CreditBalance.BatchAvailability::packageCode)
                .containsExactly(WelcomeBonus.PACKAGE_CODE);

        Entitlement entitlement = entitlementPort.find(userId).orElseThrow();
        assertThat(entitlement.highestPackage()).as("quà không phải gói đã kích hoạt").isNull();
        assertThat(entitlement.features().isEnabled(com.catcheck.credit.domain.PlanFeature.STORE_IMAGE)).isFalse();
        assertThat(entitlement.hasWriteAccessAt(grantedAt)).as("được quét bằng credit quà").isTrue();
    }

    @Test
    void welcomeCreditsAreConsumedFefoAndExpireAfterFiveDays() {
        WelcomeBonusService service = service(new WelcomeBonusProperties(true, 10, Duration.ofDays(5)));
        CreditBatchGrant welcome = service.grant(userId, "EMAIL").orElseThrow();

        ActivateCreditCodeService activation = new ActivateCreditCodeService(
                new JdbcActivationCodeAdapter(jdbc), ledgerPort, batchPort, planPort,
                hasher(), auditLog, recalculation, uuidV7, clock, new Bucket4jActivationRateLimiter(),
                new ActivationMetrics(new SimpleMeterRegistry()));
        ActivationCodeIssuanceService issuance = new ActivationCodeIssuanceService(
                new JdbcActivationCodeAdapter(jdbc), planPort, hasher(), new SecureRandom(), uuidV7, clock);
        String rawCode = issuance.issue("MINI", 1, "WELCOME-IT-" + UUID.randomUUID(), 3650).rawCodes().getFirst();
        activation.activate(userId, rawCode, ActivateCreditCodeService.RequestFacts.UNKNOWN);

        Entitlement afterActivation = entitlementPort.find(userId).orElseThrow();
        assertThat(afterActivation.highestPackage()).as("WELCOME không được lấn hạng gói thật").isEqualTo("MINI");

        FefoCreditConsumptionService consumption =
                new FefoCreditConsumptionService(ledgerPort, entitlementPort, uuidV7, clock);
        CreditConsumption.CreditCharge charge = consumption.consume(userId, new CreditConsumption.CreditConsumeCommand(
                1, "welcome-it-" + UUID.randomUUID(), CreditConsumption.ReferenceType.SCAN, UUID.randomUUID(), null));
        assertThat(charge.ledgerEntries().getFirst().batchId())
                .as("FEFO: lô quà hết hạn trước (5 ngày < 7 ngày MINI) nên bị trừ trước")
                .isEqualTo(welcome.id());
        assertThat(charge.balanceAfter()).isEqualTo(10 - 1 + 3);

        clock.set(welcome.expiresAt().plusSeconds(1));
        CreditExpiryService expiry = new CreditExpiryService(ledgerPort, batchPort, uuidV7);
        assertThat(expiry.findDueBatchIds(clock.instant(), 10)).containsExactly(welcome.id());
        assertThat(expiry.expireBatch(welcome.id(), UUID.randomUUID(), clock.instant())).isTrue();
        CreditBatchView expired = batchPort.findAllBatches(userId).stream()
                .filter(b -> b.id().equals(welcome.id())).findFirst().orElseThrow();
        assertThat(expired.status()).isEqualTo(CreditBatchStatus.EXPIRED);
        assertThat(ledgerPort.availableBalance(userId, clock.instant())).isEqualTo(3);
    }

    @Test
    void disabledBonusGrantsNothing() {
        WelcomeBonusService service = service(new WelcomeBonusProperties(false, 10, Duration.ofDays(5)));

        assertThat(service.grant(userId, "EMAIL")).isEmpty();
        assertThat(batchPort.findAllBatches(userId)).isEmpty();
    }

    private WelcomeBonusService service(WelcomeBonusProperties properties) {
        return new WelcomeBonusService(properties, batchPort, ledgerPort, entitlementPort,
                recalculation, auditLog, uuidV7, clock);
    }

    private static HmacActivationCodeHasher hasher() {
        return new HmacActivationCodeHasher(new ActivationPepperProperties(
                "welcome-it-only-activation-pepper-32-bytes-minimum-xx", 1));
    }

    private UUID insertTestUser() {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Welcome bonus test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "welcome-it-" + UUID.randomUUID() + "@example.test");
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant value) {
            this.instant = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
