package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.application.AdminActivationTestDoubles.FixedPackagePlanPort;
import com.catcheck.credit.application.AdminActivationTestDoubles.RecordingAuditLog;
import com.catcheck.credit.application.AdminCreditService.AdjustmentDirection;
import com.catcheck.credit.application.AdminCreditService.AdminCreditAdjustmentResult;
import com.catcheck.credit.application.spi.AppSettingPort;
import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditLedgerQueryPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * L9 ({@code GET /admin/users/{userId}/credits}) và L10
 * ({@code POST /admin/users/{userId}/credit-adjustments}) — p8 §8.4.12 mục (a), p14 §14.4.4.
 *
 * <p>Những tính chất được neo ở đây là những tính chất <b>sai thì mất tiền thật của người dùng</b>:
 * FEFO đúng thứ tự khi thu hồi, một dòng ledger cho mỗi lô bị trừ, {@code note} không rỗng (ràng
 * buộc DB {@code ck_credit_ledger_adjust_note}), trần an toàn chặn được, và một
 * {@code Idempotency-Key} không bao giờ cấp credit hai lần.</p>
 */
class AdminCreditServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final UUID ADMIN_ID = UUID.fromString("00000000-0000-7000-8000-0000000000a1");
    private static final UUID OTHER_ADMIN = UUID.fromString("00000000-0000-7000-8000-0000000000b2");
    private static final Instant NOW = Instant.parse("2026-10-06T09:00:00Z");

    private InMemoryCreditStore creditStore;
    private FixedPackagePlanPort packagePlanPort;
    private RecordingAuditLog auditLog;
    private FakeLedgerQueryPort ledgerQueryPort;
    private MutableAppSettingPort appSettings;
    private StoreBackedEntitlementPort entitlementPort;
    private AdminCreditService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        creditStore = new InMemoryCreditStore();
        packagePlanPort = new FixedPackagePlanPort("PLUS", 10, 30);
        auditLog = new RecordingAuditLog();
        ledgerQueryPort = new FakeLedgerQueryPort(creditStore);
        appSettings = new MutableAppSettingPort();
        entitlementPort = new StoreBackedEntitlementPort(creditStore);
        service = new AdminCreditService(
                creditStore, creditStore, ledgerQueryPort, packagePlanPort, appSettings,
                new EntitlementRecalculationService(entitlementPort, creditStore, packagePlanPort),
                auditLog, new UuidV7(clock), clock);
    }

    /* ------------------------------------------------------------------ L9 */

    @Test
    @DisplayName("L9: trả CẢ lô đã cạn/hết hạn (p5 R3 — chứng từ đối soát) + ghi audit dù chỉ đọc")
    void overviewReturnsClosedBatchesAndAudits() {
        creditStore.addBatch(USER_ID, NOW.plusSeconds(86_400), 4, 10);
        InMemoryCreditStore.Batch exhausted =
                creditStore.addBatch(USER_ID, NOW.plusSeconds(172_800), 0, 10);
        exhausted.status = CreditBatchStatus.EXHAUSTED;

        AdminCreditService.AdminCreditOverview overview =
                service.overview(USER_ID, 0, 20, context(ADMIN_ID));

        assertThat(overview.batches()).hasSize(2);
        assertThat(overview.availableBalance()).isEqualTo(4);
        assertThat(auditLog.actions()).containsExactly("ADMIN_USER_CREDITS_VIEW");
    }

    @Test
    @DisplayName("L9: size vượt trần bị kẹp về 100, không âm thầm nhận 10 000 (p8 §8.1.4)")
    void overviewClampsPageSize() {
        AdminCreditService.AdminCreditOverview overview =
                service.overview(USER_ID, -3, 10_000, context(ADMIN_ID));

        assertThat(overview.ledgerSize()).isEqualTo(AdminCreditService.MAX_PAGE_SIZE);
        assertThat(overview.ledgerPage()).isZero();
    }

    /* ----------------------------------------------------------------- L10 */

    @Test
    @DisplayName("L10 GRANT: tạo lô mới + đúng MỘT dòng GRANT, số dư tăng, entitlement mở lại quyền ghi")
    void grantCreatesBatchLedgerAndWriteAccess() {
        AdminCreditAdjustmentResult result = service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 25, "PLUS", 14, "key-grant-1", context(ADMIN_ID));

        assertThat(result.balanceBefore()).isZero();
        assertThat(result.balanceAfter()).isEqualTo(25);
        assertThat(creditStore.batches).hasSize(1);
        assertThat(creditStore.batches.getFirst().remainingAmount).isEqualTo(25);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(14L * 86_400));

        List<LedgerEntry> grants = creditStore.entriesOfType(CreditLedgerType.GRANT);
        assertThat(grants).hasSize(1);
        assertThat(grants.getFirst().refType()).isEqualTo(CreditLedgerRefType.ADMIN);
        assertThat(grants.getFirst().refId()).isEqualTo(ADMIN_ID);
        assertThat(grants.getFirst().idempotencyKey()).isEqualTo("key-grant-1");

        // Không tính lại entitlement thì credit vừa cấp vô dụng: consume() đòi hasWriteAccessAt().
        Entitlement entitlement = entitlementPort.rows.get(USER_ID);
        assertThat(entitlement).isNotNull();
        assertThat(entitlement.hasWriteAccessAt(NOW)).isTrue();

        assertThat(auditLog.actions()).containsExactly("CREDIT_ADJUST");
    }

    @Test
    @DisplayName("L10 GRANT: thiếu validityDays ⇒ lấy credit_validity_days của gói tham chiếu")
    void grantFallsBackToPlanValidity() {
        AdminCreditAdjustmentResult result = service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 5, "PLUS", null, null, context(ADMIN_ID));

        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(30L * 86_400));
    }

    @Test
    @DisplayName("L10 GRANT: gói tham chiếu không tồn tại ⇒ 404 PACKAGE_PLAN_NOT_FOUND, không tạo lô")
    void grantRejectsUnknownPackage() {
        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 5, "KHONG_CO", null, null, context(ADMIN_ID)))
                .isInstanceOf(NotFoundException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.PACKAGE_PLAN_NOT_FOUND);
        assertThat(creditStore.batches).isEmpty();
    }

    @Test
    @DisplayName("L10 REVOKE: trừ theo FEFO — lô SẮP HẾT HẠN trước, vắt sang lô sau, một dòng ADJUST mỗi lô")
    void revokeDeductsFefoAcrossBatches() {
        InMemoryCreditStore.Batch soon = creditStore.addBatch(USER_ID, NOW.plusSeconds(86_400), 3, 10);
        InMemoryCreditStore.Batch later = creditStore.addBatch(USER_ID, NOW.plusSeconds(864_000), 10, 10);

        AdminCreditAdjustmentResult result = service.adjust(
                USER_ID, AdjustmentDirection.REVOKE, 5, null, null, "key-revoke-1", context(ADMIN_ID));

        assertThat(soon.remainingAmount).isZero();
        assertThat(soon.status).isEqualTo(CreditBatchStatus.EXHAUSTED);
        assertThat(later.remainingAmount).isEqualTo(8);
        assertThat(result.balanceAfter()).isEqualTo(8);

        List<LedgerEntry> adjusts = creditStore.entriesOfType(CreditLedgerType.ADJUST);
        assertThat(adjusts).hasSize(2);
        assertThat(adjusts).allSatisfy(entry -> {
            assertThat(entry.amount()).isNegative();
            // ck_credit_ledger_adjust_note: note NOT NULL và không rỗng cho mọi dòng ADJUST.
            assertThat(entry.note()).isNotBlank();
            assertThat(entry.refType()).isEqualTo(CreditLedgerRefType.ADMIN);
        });
        // UNIQUE(idempotency_key) là một cột ⇒ chỉ dòng ĐẦU mang khoá.
        assertThat(adjusts.stream().filter(e -> e.idempotencyKey() != null)).hasSize(1);
    }

    @Test
    @DisplayName("L10 REVOKE: note của ADJUST KHÔNG chứa reason nội bộ (p14 bước 9 — không lộ admin)")
    void revokeNoteDoesNotLeakInternalReason() {
        creditStore.addBatch(USER_ID, NOW.plusSeconds(86_400), 10, 10);

        service.adjust(USER_ID, AdjustmentDirection.REVOKE, 2, null, null, null,
                context(ADMIN_ID, "Thu hoi do ticket #482 cua nhan vien Minh"));

        assertThat(creditStore.entriesOfType(CreditLedgerType.ADJUST))
                .allSatisfy(entry -> assertThat(entry.note()).doesNotContain("#482", "Minh"));
    }

    @Test
    @DisplayName("L10 REVOKE: thu hồi nhiều hơn số dư ⇒ 409 CREDIT_ADJUST_EXCEEDS_BALANCE, không ghi gì")
    void revokeBeyondBalanceIsRejected() {
        creditStore.addBatch(USER_ID, NOW.plusSeconds(86_400), 3, 10);

        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.REVOKE, 4, null, null, null, context(ADMIN_ID)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.CREDIT_ADJUST_EXCEEDS_BALANCE);
        assertThat(creditStore.entries).isEmpty();
    }

    @Test
    @DisplayName("L10: vượt trần MỘT LẦN (app_setting, mặc định 200) ⇒ 422 CREDIT_ADJUST_LIMIT_EXCEEDED")
    void perOperationLimitIsEnforced() {
        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 201, "PLUS", null, null, context(ADMIN_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.CREDIT_ADJUST_LIMIT_EXCEEDED);
        assertThat(creditStore.batches).isEmpty();
    }

    @Test
    @DisplayName("L10: trần MỖI NGÀY tính theo ĐÚNG admin đó — admin khác không bị tính dồn")
    void perDayLimitIsScopedToTheActingAdmin() {
        appSettings.values.put(AdminCreditService.KEY_MAX_PER_DAY, 30);

        service.adjust(USER_ID, AdjustmentDirection.GRANT, 20, "PLUS", null, null, context(ADMIN_ID));

        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 15, "PLUS", null, null, context(ADMIN_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.CREDIT_ADJUST_LIMIT_EXCEEDED);

        // Cùng số lượng, admin khác ⇒ đi qua: trần là "mỗi admin mỗi ngày", không phải toàn hệ thống.
        AdminCreditAdjustmentResult other = service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 15, "PLUS", null, null, context(OTHER_ADMIN));
        assertThat(other.amount()).isEqualTo(15);
    }

    @Test
    @DisplayName("L10: trần MỖI NGÀY cộng cả hai chiều theo GIÁ TRỊ TUYỆT ĐỐI — cấp rồi thu không reset hạn mức")
    void perDayLimitUsesAbsoluteValues() {
        appSettings.values.put(AdminCreditService.KEY_MAX_PER_DAY, 30);

        service.adjust(USER_ID, AdjustmentDirection.GRANT, 20, "PLUS", null, null, context(ADMIN_ID));
        service.adjust(USER_ID, AdjustmentDirection.REVOKE, 5, null, null, null, context(ADMIN_ID));

        // 20 + 5 = 25 đã dùng; còn 5. Xin 6 phải bị chặn (nếu cộng đại số thì còn 15 và sẽ cho qua).
        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 6, "PLUS", null, null, context(ADMIN_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((CatCheckException) ex).errorCode())
                .isEqualTo(CreditErrorCode.CREDIT_ADJUST_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("L10: gọi lại cùng Idempotency-Key ⇒ KHÔNG cấp lần hai, trả replayed = true")
    void sameIdempotencyKeyDoesNotGrantTwice() {
        service.adjust(USER_ID, AdjustmentDirection.GRANT, 10, "PLUS", null, "key-dup", context(ADMIN_ID));
        AdminCreditAdjustmentResult replay = service.adjust(
                USER_ID, AdjustmentDirection.GRANT, 10, "PLUS", null, "key-dup", context(ADMIN_ID));

        assertThat(replay.replayed()).isTrue();
        assertThat(creditStore.batches).hasSize(1);
        assertThat(creditStore.entriesOfType(CreditLedgerType.GRANT)).hasSize(1);
        // Replay không được sinh dòng audit thứ hai: side effect không xảy ra thì không có gì để ghi.
        assertThat(auditLog.actions()).containsExactly("CREDIT_ADJUST");
    }

    @Test
    @DisplayName("L10: amount <= 0 bị từ chối — dấu do direction quyết định, không do client gửi số âm")
    void nonPositiveAmountIsRejected() {
        assertThatThrownBy(() -> service.adjust(
                USER_ID, AdjustmentDirection.GRANT, -5, "PLUS", null, null, context(ADMIN_ID)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /* --------------------------------------------------------------- helper */

    private static AdminActionContext context(UUID adminId) {
        return context(adminId, "Bồi hoàn do lỗi hệ thống, ticket #482");
    }

    private static AdminActionContext context(UUID adminId, String reason) {
        return new AdminActionContext(adminId, "ADMIN_SUPER", reason, "req-1", "203.0.113.9", "junit");
    }

    /** {@code credit_ledger} đọc theo offset, dựng từ chính các dòng mà store đã ghi. */
    private static final class FakeLedgerQueryPort implements CreditLedgerQueryPort {

        private final InMemoryCreditStore store;

        private FakeLedgerQueryPort(InMemoryCreditStore store) {
            this.store = store;
        }

        @Override
        public LedgerPage findByUser(UUID userId, LedgerCursor cursor, int limit) {
            throw new UnsupportedOperationException("L9 dùng đường offset");
        }

        @Override
        public LedgerOffsetPage findByUserForAdmin(UUID userId, int offset, int limit) {
            List<LedgerRow> all = store.entries.stream()
                    .filter(entry -> entry.userId().equals(userId))
                    .sorted(Comparator.comparing(LedgerEntry::createdAt).reversed())
                    .map(entry -> new LedgerRow(entry.id(), entry.type(), entry.amount(),
                            entry.balanceAfter(), entry.batchId(), "PLUS", entry.refType(),
                            entry.note(), entry.createdAt()))
                    .toList();
            List<LedgerRow> page = all.stream().skip(offset).limit(limit).toList();
            return new LedgerOffsetPage(page, all.size());
        }
    }

    /** {@code app_setting} sửa được trong test — mặc định rỗng ⇒ dùng mặc định trong mã. */
    private static final class MutableAppSettingPort implements AppSettingPort {

        private final Map<String, Integer> values = new HashMap<>();

        @Override
        public OptionalInt findInt(String key) {
            Integer value = values.get(key);
            return value == null ? OptionalInt.empty() : OptionalInt.of(value);
        }
    }

    /**
     * {@code user_entitlement} trong bộ nhớ, {@code maxActivatedBatchExpiry} tính THẬT từ các lô
     * của store — khác {@code AdminActivationTestDoubles.FakeEntitlementPort} (luôn trả rỗng).
     * Nếu trả rỗng thì {@code write_access_until} luôn null và test "credit admin cấp dùng được"
     * sẽ xanh một cách vô nghĩa.
     */
    private static final class StoreBackedEntitlementPort implements UserEntitlementPort {

        private final Map<UUID, Entitlement> rows = new HashMap<>();
        private final InMemoryCreditStore store;

        private StoreBackedEntitlementPort(InMemoryCreditStore store) {
            this.store = store;
        }

        @Override
        public Optional<Entitlement> find(UUID userId) {
            return Optional.ofNullable(rows.get(userId));
        }

        @Override
        public Entitlement findOrDefault(UUID userId, Instant now) {
            return rows.getOrDefault(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void ensureRow(UUID userId, Instant now) {
            rows.putIfAbsent(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void save(Entitlement entitlement) {
            rows.put(entitlement.userId(), entitlement);
        }

        @Override
        public boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now) {
            return false;
        }

        @Override
        public Optional<Instant> maxActivatedBatchExpiry(UUID userId) {
            return store.batches.stream()
                    .filter(batch -> batch.userId.equals(userId))
                    .map(batch -> batch.expiresAt)
                    .max(Comparator.naturalOrder());
        }
    }
}
