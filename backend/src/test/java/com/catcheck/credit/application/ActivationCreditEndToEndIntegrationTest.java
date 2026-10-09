package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.application.AdminActivationTestDoubles.RecordingAuditLog;
import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.ActivationRateLimiter;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerPage;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort.LedgerRow;
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
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.RateLimitedException;
import com.catcheck.shared.id.UuidV7;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * G7 ({@code docs/cat-rag-credit-pipeline.md} B2) — nhánh kích hoạt mã <b>thành công</b> chạy
 * thật trên PostgreSQL local, từ đầu đến cuối: phát hành mã {@code ISSUED} mới → kích hoạt →
 * {@code credit_batch} đúng {@code expires_at} → quét giả lập trừ đúng FEFO → advance
 * {@link Clock} qua hạn → job hết hạn (gọi logic trực tiếp, {@link CreditExpiryService}) →
 * batch {@code EXPIRED}, quyền đọc giữ, quyền ghi hết.
 *
 * <p><b>Vì sao bài này cần chạy thật thay vì fake</b> (cùng lý do
 * {@code com.catcheck.reminder.SendDueRemindersJobIntegrationTest} đã ghi): mọi test khác của
 * {@code ActivateCreditCodeService} tới trước gói việc này đều dùng
 * {@code InMemoryCreditStore}/{@code AdminActivationTestDoubles} — chúng chứng minh được
 * <i>logic</i> đúng nhưng không chứng minh được <i>SQL thật</i> đúng (JOIN
 * {@code credit_batch}/{@code credit_ledger}, cột {@code expires_at} JSONB của
 * {@code package_plan.features}, các {@code CHECK} ràng buộc dấu/khoảng của schema thật).</p>
 *
 * <p><b>Không Testcontainers/Docker</b> (chủ dự án cấm build Docker ở máy này) — nối thẳng
 * Postgres local {@code catcheck} mà {@code backend/run-local.sh} cũng dùng, cùng cách
 * {@code scan.tooling.PipelineAccuracyHarness}/{@code LocalDbChartCatalog} đã làm cho module
 * {@code scan} (xem {@code backend/README.md}: "không Docker/Testcontainers" là tiền đề chung
 * của mọi test tích hợp trong repo này). Không dựng {@code ApplicationContext} Spring: mọi
 * service/adapter ở đây có constructor thuần (không cần bean Spring) nên nối tay được, tương tự
 * {@code LocalDbChartCatalog} nối tay {@code ChartCatalog} thay vì dựng ba repository JPA.</p>
 *
 * <p>Dữ liệu dùng gói {@code MINI} (3 credit/7 ngày) đã có sẵn trong {@code package_plan} —
 * KHÔNG tạo gói mới, KHÔNG đổi {@code credit_validity_days}/giá của gói nào (ràng buộc cứng của
 * gói việc này). Mỗi test tự tạo {@code app_user} riêng (KHÔNG đụng tài khoản demo
 * {@code uireview@catcheck.vn}) và tự xoá sạch trong {@link #cleanUp()} — không annotation
 * {@code @Transactional} nào bọc ngoài vì các service thật dùng Bucket4j/Caffeine in-memory
 * không rollback được qua annotation đó.</p>
 */
class ActivationCreditEndToEndIntegrationTest {

    /** Khớp {@code application-local.yml} — DB native mà {@code run-local.sh} cũng dùng. */
    private static final String JDBC_URL = "jdbc:postgresql://localhost:5432/catcheck";
    private static final String DB_USER = "catcheck";
    private static final String DB_PASSWORD = "catcheck_local_only";

    /** Gói có sẵn trong {@code package_plan}, validity ngắn nhất (7 ngày) — không tạo gói mới. */
    private static final String PACKAGE_CODE = "MINI";
    private static final int PACKAGE_CREDITS = 3;
    private static final int PACKAGE_VALIDITY_DAYS = 7;

    private JdbcTemplate jdbc;
    private MutableClock clock;
    private ActivateCreditCodeService activateService;
    private ActivationCodeIssuanceService issuanceService;
    private FefoCreditConsumptionService consumptionService;
    private CreditExpiryService expiryService;
    private CreditBatchPort batchPort;
    private CreditLedgerQueryPort ledgerQueryPort;
    private UserEntitlementPort entitlementPort;
    private RecordingAuditLog auditLog;

    /** Khi khác {@code null}, {@link #cleanUp()} xoá mọi dòng đã tạo cho user này. */
    private UUID createdUserId;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, DB_USER, DB_PASSWORD);
        jdbc = new JdbcTemplate(dataSource);

        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

        ActivationCodePort codePort = new JdbcActivationCodeAdapter(jdbc);
        batchPort = new JdbcCreditBatchAdapter(jdbc);
        CreditLedgerPort ledgerPort = new JdbcCreditLedgerAdapter(jdbc);
        ledgerQueryPort = new JdbcCreditLedgerQueryAdapter(jdbc);
        JsonMapper jsonMapper = JsonMapper.builder().build();
        PackagePlanPort planPort = new JdbcPackagePlanAdapter(jdbc, jsonMapper);
        entitlementPort = new JdbcUserEntitlementAdapter(jdbc, jsonMapper);
        // Pepper giả CHỈ DÙNG CHO TEST NÀY — cùng quy ước với ACTIVATION_PEPPER của
        // application-test.yml (>= 32 byte, không phải giá trị thật).
        ActivationCodeHasher hasher = new HmacActivationCodeHasher(
                new ActivationPepperProperties(
                        "e2e-test-only-activation-pepper-32-bytes-minimum-xxx", 1));

        UuidV7 uuidV7 = new UuidV7(clock);
        auditLog = new RecordingAuditLog();
        EntitlementRecalculationService recalculation =
                new EntitlementRecalculationService(entitlementPort, batchPort, planPort);
        ActivationRateLimiter rateLimiter = new Bucket4jActivationRateLimiter();
        ActivationMetrics metrics = new ActivationMetrics(new SimpleMeterRegistry());

        activateService = new ActivateCreditCodeService(
                codePort, ledgerPort, batchPort, planPort, hasher, auditLog, recalculation,
                uuidV7, clock, rateLimiter, metrics);
        issuanceService = new ActivationCodeIssuanceService(
                codePort, planPort, hasher, new SecureRandom(), uuidV7, clock);
        consumptionService = new FefoCreditConsumptionService(ledgerPort, entitlementPort, uuidV7, clock);
        expiryService = new CreditExpiryService(ledgerPort, batchPort, uuidV7);
    }

    @AfterEach
    void cleanUp() {
        if (createdUserId == null) {
            return;
        }
        jdbc.update("DELETE FROM credit_ledger WHERE user_id = ?", createdUserId);
        jdbc.update("DELETE FROM credit_batch WHERE user_id = ?", createdUserId);
        jdbc.update("DELETE FROM activation_code WHERE redeemed_by = ?", createdUserId);
        jdbc.update("DELETE FROM user_entitlement WHERE user_id = ?", createdUserId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", createdUserId);
    }

    @Test
    @DisplayName("G7: phat hanh -> kich hoat -> quet FEFO -> het han => doc giu, ghi khoa")
    void issueActivateConsumeThenExpire_readAccessKeptWriteAccessRevoked() {
        createdUserId = insertTestUser();

        /* ---- 1. phát hành một mã ISSUED mới qua ActivationCodeIssuanceService ---- */
        ActivationCodeIssuanceService.IssuedBatch issued = issuanceService.issue(
                PACKAGE_CODE, 1, "E2E-" + UUID.randomUUID(), 3650);
        String rawCode = issued.rawCodes().getFirst();

        /* ---- 2. kích hoạt ---- */
        ActivateCreditCodeService.ActivationResult activation = activateService.activate(
                createdUserId, rawCode, ActivateCreditCodeService.RequestFacts.UNKNOWN);

        Instant activatedAt = clock.instant();
        Instant expectedExpiry = activatedAt.plus(Duration.ofDays(PACKAGE_VALIDITY_DAYS));

        assertThat(activation.packageCode()).isEqualTo(PACKAGE_CODE);
        assertThat(activation.creditsGranted()).isEqualTo(PACKAGE_CREDITS);
        assertThat(activation.expiresAt()).isEqualTo(expectedExpiry);
        assertThat(activation.balanceAfter()).isEqualTo(PACKAGE_CREDITS);
        assertThat(auditLog.actions()).containsExactly("CREDIT_ACTIVATION_REDEEMED");

        /* ---- 3. credit_batch thật trong DB đúng expires_at (bất biến "hạn tính từ lúc kích
               hoạt", p5 R1) ---- */
        List<CreditBatchView> batchesAfterActivation = batchPort.findAllBatches(createdUserId);
        assertThat(batchesAfterActivation).hasSize(1);
        CreditBatchView batch = batchesAfterActivation.getFirst();
        assertThat(batch.status()).isEqualTo(CreditBatchStatus.ACTIVE);
        assertThat(batch.expiresAt()).isEqualTo(expectedExpiry);
        assertThat(batch.remainingAmount()).isEqualTo(PACKAGE_CREDITS);

        /* ---- 4. quét giả lập trừ 2 credit theo FEFO — chỉ một lô nên toàn bộ về lô đó ---- */
        CreditConsumption.CreditCharge charge = consumptionService.consume(createdUserId,
                new CreditConsumption.CreditConsumeCommand(
                        2, "e2e-scan-1", CreditConsumption.ReferenceType.SCAN,
                        UUID.randomUUID(), null));

        assertThat(charge.chargedCredits()).isEqualTo(2);
        assertThat(charge.balanceAfter()).isEqualTo(1);
        assertThat(charge.ledgerEntries()).hasSize(1);
        assertThat(charge.ledgerEntries().getFirst().batchId()).isEqualTo(batch.id());
        assertThat(charge.ledgerEntries().getFirst().amount()).isEqualTo(2);

        /* ---- 5. G4: mỗi dòng lịch sử lộ packageCode + expiresAt của lô bị trừ ---- */
        LedgerPage pageAfterConsume = ledgerQueryPort.findByUser(createdUserId, null, 20);
        assertThat(pageAfterConsume.entries()).hasSize(2); // GRANT + CONSUME
        for (LedgerRow row : pageAfterConsume.entries()) {
            assertThat(row.packageCode()).as("dòng %s phải lộ mã gói của lô", row.type()).isEqualTo(PACKAGE_CODE);
            assertThat(row.expiresAt()).as("dòng %s phải lộ hạn của lô", row.type()).isEqualTo(expectedExpiry);
        }

        /* ---- 6. advance Clock qua expires_at, gọi logic hết hạn trực tiếp (không cần
               ExpireCreditBatchesJob/JobRunner/ShedLock cho một bài test đơn luồng) ---- */
        clock.advanceTo(expectedExpiry.plus(Duration.ofDays(1)));
        List<UUID> due = expiryService.findDueBatchIds(clock.instant(), 10);
        assertThat(due).containsExactly(batch.id());
        boolean expired = expiryService.expireBatch(batch.id(), UUID.randomUUID(), clock.instant());
        assertThat(expired).isTrue();
        // Idempotent: gọi lại không đóng được nữa (đã EXPIRED, không còn khớp bộ lọc).
        assertThat(expiryService.expireBatch(batch.id(), UUID.randomUUID(), clock.instant())).isFalse();

        List<CreditBatchView> batchesAfterExpiry = batchPort.findAllBatches(createdUserId);
        assertThat(batchesAfterExpiry).hasSize(1);
        assertThat(batchesAfterExpiry.getFirst().status()).isEqualTo(CreditBatchStatus.EXPIRED);
        assertThat(batchesAfterExpiry.getFirst().remainingAmount()).isZero();

        LedgerPage pageAfterExpiry = ledgerQueryPort.findByUser(createdUserId, null, 20);
        assertThat(pageAfterExpiry.entries()).hasSize(3); // GRANT + CONSUME + EXPIRE
        LedgerRow expireRow = pageAfterExpiry.entries().getFirst(); // mới nhất trước
        assertThat(expireRow.amount()).isEqualTo(-1); // đúng phần còn lại (3 - 2 = 1)
        assertThat(expireRow.expiresAt())
                .as("lô đã EXPIRED vẫn phải lộ đúng mốc hết hạn gốc, không phải null")
                .isEqualTo(expectedExpiry);

        /* ---- 7. quyền ĐỌC giữ (highestPackage không mất), quyền GHI hết ---- */
        Entitlement entitlement = entitlementPort.find(createdUserId).orElseThrow();
        assertThat(entitlement.highestPackage()).isEqualTo(PACKAGE_CODE);
        assertThat(entitlement.hasWriteAccessAt(clock.instant()))
                .as("p5 R5: quyền ghi/tạo mới phải hết cùng lúc lô cuối cùng hết hạn")
                .isFalse();

        /* ---- 8. hệ quả quan sát được: quét mới KHÔNG trừ được nữa (hết credit) ---- */
        assertThatThrownBy(() -> consumptionService.consume(createdUserId,
                new CreditConsumption.CreditConsumeCommand(1, "e2e-scan-2",
                        CreditConsumption.ReferenceType.SCAN, UUID.randomUUID(), null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.CREDIT_INSUFFICIENT);
    }

    /**
     * G3 — xác nhận hạn mức thật (Bucket4j, không fake) chặn đúng ở lần thứ 6 và trả
     * {@code Retry-After} > 0. Dùng mã sai định dạng vì giới hạn áp dụng TRƯỚC bước kiểm định
     * dạng (xem {@code ActivateCreditCodeService.doActivate}) — không cần tạo user/mã thật trong
     * DB cho bài test này.
     */
    @Test
    @DisplayName("G3: lan goi thu 6 trong 15 phut cua cung mot user bi 429 kem Retry-After")
    void sixthActivationAttemptForSameUserIsRateLimited() {
        UUID userId = UUID.randomUUID(); // không insert app_user: không chạm DB ở nhánh này

        for (int attempt = 1; attempt <= 5; attempt++) {
            int currentAttempt = attempt;
            assertThatThrownBy(() -> activateService.activate(
                    userId, "KHONG-DUNG-DINH-DANG", ActivateCreditCodeService.RequestFacts.UNKNOWN))
                    .as("lần gọi thứ %d phải còn qua được giới hạn (sai mã, không phải 429)", currentAttempt)
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting(ex -> ((CatCheckException) ex).errorCode())
                    .isEqualTo(CreditErrorCode.ACTIVATION_CODE_MALFORMED);
        }

        assertThatThrownBy(() -> activateService.activate(
                userId, "KHONG-DUNG-DINH-DANG", ActivateCreditCodeService.RequestFacts.UNKNOWN))
                .isInstanceOf(RateLimitedException.class)
                .satisfies(ex -> {
                    RateLimitedException rateLimited = (RateLimitedException) ex;
                    assertThat(rateLimited.errorCode()).isEqualTo(CreditErrorCode.ACTIVATION_RATE_LIMITED);
                    assertThat(rateLimited.retryAfterSeconds()).isPositive();
                });
    }

    private UUID insertTestUser() {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'G7 credit e2e test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "credit-e2e-" + UUID.randomUUID() + "@example.test");
    }

    /** {@link Clock} có thể chỉnh tay — dùng để "advance qua expires_at" (G7). */
    private static final class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void advanceTo(Instant newInstant) {
            this.instant = newInstant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
