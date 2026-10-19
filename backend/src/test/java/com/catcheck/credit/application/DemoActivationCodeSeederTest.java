package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.application.AdminActivationTestDoubles.AllowAllRateLimiter;
import com.catcheck.credit.application.AdminActivationTestDoubles.FakeEntitlementPort;
import com.catcheck.credit.application.AdminActivationTestDoubles.FixedPackagePlanPort;
import com.catcheck.credit.application.AdminActivationTestDoubles.InMemoryActivationCodePort;
import com.catcheck.credit.application.AdminActivationTestDoubles.RecordingAuditLog;
import com.catcheck.credit.application.AdminActivationTestDoubles.TestCodeHasher;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeEffectiveStatus;
import com.catcheck.credit.domain.ActivationCodeFormat;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.id.UuidV7;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoActivationCodeSeederTest {

    private static final Instant NOW = Instant.parse("2026-10-14T08:00:00Z");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000c001");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InMemoryActivationCodePort codePort;
    private FixedPackagePlanPort packagePlanPort;
    private TestCodeHasher codeHasher;
    private DemoActivationCodeSeeder seeder;

    @BeforeEach
    void setUp() {
        codePort = new InMemoryActivationCodePort();
        // Chỉ có gói PLUS: các gói demo khác phải bị bỏ qua chứ không làm hỏng seed.
        packagePlanPort = new FixedPackagePlanPort("PLUS", 10, 7);
        codeHasher = new TestCodeHasher();
        seeder = new DemoActivationCodeSeeder(codePort, packagePlanPort, codeHasher, new UuidV7(clock), clock);
    }

    @Test
    @DisplayName("Mã demo cố định đúng định dạng/checksum và khớp tài liệu docs/DEMO_ACCOUNTS.md")
    void demoCodesAreValidAndStable() {
        assertThat(DemoActivationCodeSeeder.demoCodes())
                .hasSize(26)
                .allSatisfy(demo -> assertThat(ActivationCodeFormat.validateOrNull(demo.rawCode()))
                        .isEqualTo(demo.rawCode()))
                .extracting(DemoActivationCodeSeeder.DemoCode::rawCode)
                .doesNotHaveDuplicates()
                .contains("CC-MINI-DEM000001C", "CC-DAILY-DEM0000058", "CC-PLUS-DEM000003A",
                        "CC-MULTI-DEM0000049", "CC-CARE_BOX-DEM000002B", "CC-PLUS-EXP000001R");
    }

    @Test
    @DisplayName("Seed idempotent: chạy lại không chèn trùng, không reset mã đã REDEEMED")
    void seedIsIdempotentAndNeverResetsRedeemed() {
        assertThat(seeder.seed()).isEqualTo(6);
        assertThat(codePort.rows).hasSize(6);

        ActivationCode redeemed = codePort.findByCodeHash(codeHasher.hash("CC-PLUS-DEM000001C").hex())
                .orElseThrow();
        codePort.markRedeemed(redeemed.id(), USER_ID, NOW);

        assertThat(seeder.seed()).isZero();
        assertThat(codePort.rows).hasSize(6);
        assertThat(codePort.findById(redeemed.id())).get()
                .extracting(ActivationCode::status).isEqualTo(ActivationCodeStatus.REDEEMED);
    }

    @Test
    @DisplayName("Mã demo còn hạn 365 ngày, mã mẫu hết hạn có valid_until trong quá khứ")
    void seededValidityWindows() {
        seeder.seed();

        ActivationCode valid = codePort.findByCodeHash(codeHasher.hash("CC-PLUS-DEM000002B").hex()).orElseThrow();
        assertThat(valid.productionBatch()).isEqualTo("DEMO");
        assertThat(valid.validUntil()).isEqualTo(NOW.plus(365, ChronoUnit.DAYS));
        assertThat(valid.effectiveStatusAt(NOW)).isEqualTo(ActivationCodeEffectiveStatus.ISSUED);

        ActivationCode expired = codePort.findByCodeHash(codeHasher.hash("CC-PLUS-EXP000001R").hex()).orElseThrow();
        assertThat(expired.status()).isEqualTo(ActivationCodeStatus.ISSUED);
        assertThat(expired.productionBatch()).isEqualTo("DEMO-EXPIRED");
        assertThat(expired.validUntil()).isBefore(NOW);
        assertThat(expired.effectiveStatusAt(NOW)).isEqualTo(ActivationCodeEffectiveStatus.EXPIRED);
    }

    @Test
    @DisplayName("Kích hoạt: mã demo còn hạn cộng credit, mã hết hạn ⇒ ACTIVATION_CODE_EXPIRED và không đổi trạng thái")
    void redeemingSeededCodes() {
        seeder.seed();
        InMemoryCreditStore creditStore = new InMemoryCreditStore();
        ActivateCreditCodeService activateService = new ActivateCreditCodeService(
                codePort, creditStore, creditStore, packagePlanPort, codeHasher, new RecordingAuditLog(),
                new EntitlementRecalculationService(new FakeEntitlementPort(), creditStore, packagePlanPort),
                new UuidV7(clock), clock, new AllowAllRateLimiter(),
                new ActivationMetrics(new SimpleMeterRegistry()));

        assertThatThrownBy(() -> activateService.activate(
                USER_ID, "cc-plus-exp000001r", ActivateCreditCodeService.RequestFacts.UNKNOWN))
                .isInstanceOf(CatCheckException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_CODE_EXPIRED);
        assertThat(codePort.findByCodeHash(codeHasher.hash("CC-PLUS-EXP000001R").hex())).get()
                .extracting(ActivationCode::status).isEqualTo(ActivationCodeStatus.ISSUED);
        assertThat(creditStore.batches).isEmpty();

        ActivateCreditCodeService.ActivationResult result = activateService.activate(
                USER_ID, "CC-PLUS-DEM0000049", ActivateCreditCodeService.RequestFacts.UNKNOWN);
        assertThat(result.creditsGranted()).isEqualTo(10);
        assertThat(codePort.findByCodeHash(codeHasher.hash("CC-PLUS-DEM0000049").hex())).get()
                .extracting(ActivationCode::status).isEqualTo(ActivationCodeStatus.REDEEMED);
    }
}
