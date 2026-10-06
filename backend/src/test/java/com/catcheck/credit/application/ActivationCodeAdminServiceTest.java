package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.application.AdminActivationTestDoubles.FakeEntitlementPort;
import com.catcheck.credit.application.AdminActivationTestDoubles.FixedPackagePlanPort;
import com.catcheck.credit.application.AdminActivationTestDoubles.InMemoryActivationCodePort;
import com.catcheck.credit.application.AdminActivationTestDoubles.RecordingAuditLog;
import com.catcheck.credit.application.AdminActivationTestDoubles.TestCodeHasher;
import com.catcheck.credit.domain.ActivationBatchSummary;
import com.catcheck.credit.domain.ActivationCode;
import com.catcheck.credit.domain.ActivationCodeFilter;
import com.catcheck.credit.domain.ActivationCodeStatus;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L20–L24 (p8 §8.4.12 mục (b)).
 *
 * <p>Bốn tính chất mà gói việc W2-C phải chứng minh: <b>số lượng đúng</b>, <b>mã không trùng</b>,
 * <b>void rồi không kích hoạt được</b>, <b>CSV chỉ tải được một lần</b>. Ba cái đầu là điều kiện
 * để phát hành credit mà không mất tiền; cái cuối là hợp đồng {@code 410} của p8 L22.</p>
 */
class ActivationCodeAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T08:00:00Z");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-00000000a001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-00000000b001");

    private InMemoryActivationCodePort codePort;
    private FixedPackagePlanPort packagePlanPort;
    private TestCodeHasher codeHasher;
    private RecordingAuditLog auditLog;
    private ActivationCsvVault csvVault;
    private ActivationCodeAdminService adminService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        codePort = new InMemoryActivationCodePort();
        packagePlanPort = new FixedPackagePlanPort("PLUS", 10, 7);
        codeHasher = new TestCodeHasher();
        auditLog = new RecordingAuditLog();
        csvVault = new ActivationCsvVault(clock);
        ActivationCodeIssuanceService issuanceService = new ActivationCodeIssuanceService(
                codePort, packagePlanPort, codeHasher, new SecureRandom(), new UuidV7(clock), clock);
        adminService = new ActivationCodeAdminService(
                codePort, issuanceService, csvVault, auditLog);
    }

    @Test
    @DisplayName("L20: sinh đúng số mã yêu cầu, mỗi mã một dòng ISSUED trong lô")
    void issuesExactlyRequestedQuantity() {
        ActivationBatchSummary batch = adminService.issueBatch("PLUS", 5, "LOT-A", 365, context());

        assertThat(batch.totalCodes()).isEqualTo(5);
        assertThat(batch.issuedCodes()).isEqualTo(5);
        assertThat(codePort.rows).hasSize(5);
        assertThat(codePort.rows.values())
                .allSatisfy(code -> {
                    assertThat(code.status()).isEqualTo(ActivationCodeStatus.ISSUED);
                    assertThat(code.packageCode()).isEqualTo("PLUS");
                    assertThat(code.productionBatch()).isEqualTo("LOT-A");
                    assertThat(code.codePrefix()).isEqualTo("PLUS-");
                });
        assertThat(codePort.findBatch("LOT-A")).get()
                .extracting(ActivationBatchSummary::totalCodes).isEqualTo(5L);
        assertThat(auditLog.actions()).containsExactly("ACTIVATION_CODE_BATCH_CREATE");
    }

    @Test
    @DisplayName("L20: 200 mã trong một lô, không hash nào trùng và CSV có đúng 200 dòng dữ liệu")
    void issuedCodesAreUnique() {
        adminService.issueBatch("PLUS", 200, "LOT-B", 365, context());

        Set<String> hashes = codePort.rows.values().stream()
                .map(ActivationCode::codeHash)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(hashes).hasSize(200);

        String csv = adminService.downloadCsv("LOT-B", context());
        List<String> lines = csv.lines().toList();
        assertThat(lines).hasSize(201);
        assertThat(lines.getFirst()).isEqualTo("code,package_code,production_batch,valid_until");
        Set<String> rawCodes = lines.stream().skip(1)
                .map(line -> line.split(",", 2)[0])
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        assertThat(rawCodes).hasSize(200);
        // Mã thô phải khớp định dạng p5 §5.9 CC-<PKG>-<10 ký tự Crockford, có checksum đúng.
        assertThat(rawCodes).allSatisfy(raw ->
                assertThat(com.catcheck.credit.domain.ActivationCodeFormat.validateOrNull(raw))
                        .as("mã %s phải hợp lệ cả định dạng lẫn checksum", raw)
                        .isEqualTo(raw));
    }

    @Test
    @DisplayName("L20: vượt trần 50 000 ⇒ 422 ACTIVATION_BATCH_TOO_LARGE, không ghi dòng nào")
    void rejectsBatchLargerThanContractLimit() {
        assertThatThrownBy(() -> adminService.issueBatch("PLUS", 50_001, "LOT-C", 365, context()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_BATCH_TOO_LARGE);
        assertThat(codePort.rows).isEmpty();
        assertThat(auditLog.events).isEmpty();
    }

    @Test
    @DisplayName("L20: trùng productionBatch ⇒ 409 ACTIVATION_BATCH_EXISTS (định danh lô phải duy nhất)")
    void rejectsDuplicateProductionBatch() {
        adminService.issueBatch("PLUS", 2, "LOT-D", 365, context());

        assertThatThrownBy(() -> adminService.issueBatch("PLUS", 2, "LOT-D", 365, context()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_BATCH_EXISTS);
        assertThat(codePort.rows).hasSize(2);
    }

    @Test
    @DisplayName("L22: CSV tải lần hai ⇒ 410 ACTIVATION_CSV_ALREADY_DOWNLOADED")
    void csvIsDownloadableExactlyOnce() {
        adminService.issueBatch("PLUS", 3, "LOT-E", 365, context());

        assertThat(adminService.csvAvailable("LOT-E")).isTrue();
        assertThat(adminService.downloadCsv("LOT-E", context()).lines()).hasSize(4);
        assertThat(adminService.csvAvailable("LOT-E")).isFalse();

        assertThatThrownBy(() -> adminService.downloadCsv("LOT-E", context()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_CSV_ALREADY_DOWNLOADED);
        assertThat(auditLog.actions())
                .containsExactly("ACTIVATION_CODE_BATCH_CREATE", "ACTIVATION_CODE_CSV_DOWNLOAD");
    }

    @Test
    @DisplayName("L23 + H1: mã đã void thì kích hoạt trả ACTIVATION_CODE_INVALID và KHÔNG tạo credit")
    void voidedCodeCannotBeActivated() {
        adminService.issueBatch("PLUS", 2, "LOT-F", 365, context());
        String rawCode = firstRawCodeOf("LOT-F");
        ActivationCode target = codePort.findByCodeHash(codeHasher.hash(rawCode).hex()).orElseThrow();

        ActivationCode voided = adminService.voidCode(target.id(), context());
        assertThat(voided.status()).isEqualTo(ActivationCodeStatus.VOID);
        assertThat(auditLog.actions()).contains("ACTIVATION_CODE_VOID");

        InMemoryCreditStore creditStore = new InMemoryCreditStore();
        FakeEntitlementPort entitlementPort = new FakeEntitlementPort();
        // `UserEntitlementPort` không còn là phụ thuộc của ActivateCreditCodeService: phép tính
        // entitlement đã chuyển sang EntitlementRecalculationService (W5-A) để đường admin cấp
        // credit tay (L10) dùng chung đúng một định nghĩa `write_access_until` (bất biến I28).
        EntitlementRecalculationService entitlementRecalculation =
                new EntitlementRecalculationService(entitlementPort, creditStore, packagePlanPort);
        ActivateCreditCodeService activateService = new ActivateCreditCodeService(
                codePort, creditStore, creditStore, packagePlanPort,
                codeHasher, auditLog, entitlementRecalculation, new UuidV7(clock), clock);

        assertThatThrownBy(() -> activateService.activate(
                USER_ID, rawCode, ActivateCreditCodeService.RequestFacts.UNKNOWN))
                .isInstanceOf(CatCheckException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_CODE_INVALID);
        assertThat(creditStore.batches).isEmpty();
        assertThat(creditStore.entries).isEmpty();
    }

    @Test
    @DisplayName("L23: void một mã đã REDEEMED ⇒ 409, không được biến mã đã đổi thành VOID")
    void redeemedCodeCannotBeVoided() {
        adminService.issueBatch("PLUS", 1, "LOT-G", 365, context());
        ActivationCode code = codePort.rows.values().iterator().next();
        codePort.markRedeemed(code.id(), USER_ID, NOW);

        assertThatThrownBy(() -> adminService.voidCode(code.id(), context()))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED);
        assertThat(codePort.findById(code.id())).get()
                .extracting(ActivationCode::status).isEqualTo(ActivationCodeStatus.REDEEMED);
    }

    @Test
    @DisplayName("L24: void cả lô chỉ đụng mã ISSUED, mã REDEEMED giữ nguyên")
    void voidBatchLeavesRedeemedCodesAlone() {
        adminService.issueBatch("PLUS", 4, "LOT-H", 365, context());
        ActivationCode redeemed = codePort.rows.values().iterator().next();
        codePort.markRedeemed(redeemed.id(), USER_ID, NOW);

        int voided = adminService.voidBatch("LOT-H", context());

        assertThat(voided).isEqualTo(3);
        assertThat(codePort.findBatch("LOT-H")).get().satisfies(batch -> {
            assertThat(batch.voidedCodes()).isEqualTo(3);
            assertThat(batch.redeemedCodes()).isEqualTo(1);
            assertThat(batch.issuedCodes()).isZero();
        });
    }

    @Test
    @DisplayName("L19: lọc theo lô và theo trạng thái dùng cùng bộ điều kiện cho danh sách và tổng số")
    void searchFiltersByBatchAndStatus() {
        adminService.issueBatch("PLUS", 3, "LOT-I", 365, context());
        adminService.issueBatch("PLUS", 2, "LOT-J", 365, context());
        adminService.voidBatch("LOT-J", context());

        ActivationCodeAdminService.Page<ActivationCode> issued = adminService.search(
                new ActivationCodeFilter(null, "PLUS", ActivationCodeStatus.ISSUED, null), 0, 20);
        assertThat(issued.items()).hasSize(3);
        assertThat(issued.totalElements()).isEqualTo(3);

        ActivationCodeAdminService.Page<ActivationCode> inBatchJ = adminService.search(
                new ActivationCodeFilter(null, null, null, "LOT-J"), 0, 20);
        assertThat(inBatchJ.items()).hasSize(2);
        assertThat(inBatchJ.items()).allMatch(code -> code.status() == ActivationCodeStatus.VOID);
    }

    /* ---------------------------------------------------------------- helper */

    /**
     * Lấy một mã thô của lô bằng cách rút CSV. Đây là đường DUY NHẤT đọc được mã thô, kể cả
     * trong test — nếu test có đường khác thì nó không còn kiểm đúng cái mà p5 §5.9 đảm bảo.
     */
    private String firstRawCodeOf(String batchId) {
        String csv = adminService.downloadCsv(batchId, context());
        return csv.lines().skip(1).findFirst().orElseThrow().split(",", 2)[0];
    }

    private AdminActionContext context() {
        return new AdminActionContext(ADMIN_ID, "ADMIN_SUPER",
                "kiem thu phat hanh lo ma", "req-1", "127.0.0.1", "junit");
    }
}
