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

/**
 * Hai endpoint credit ở màn quản trị — <b>L9</b> ({@code GET /admin/users/{userId}/credits}) và
 * <b>L10</b> ({@code POST /admin/users/{userId}/credit-adjustments}), p8 §8.4.12 mục (a); luồng
 * đầy đủ ở p14 §14.4.4.
 *
 * <p><b>L10 là endpoint duy nhất trong hệ thống tạo credit ngoài một mã kích hoạt</b>, nên nó
 * được viết theo đúng thứ tự p14 §14.4.4 bước 8 và không đi đường tắt nào:</p>
 * <ul>
 *   <li><b>Chiều cấp thêm</b> ⇒ {@code credit_batch} mới ({@code activation_code_id = NULL}) +
 *       một dòng {@code credit_ledger(GRANT)}. Hai thao tác trong CÙNG transaction, nếu không
 *       bất biến I1 ({@code initial + SUM(ledger) = remaining}) vi phạm ngay ở lần đọc số dư kế
 *       tiếp.</li>
 *   <li><b>Chiều thu hồi</b> ⇒ các dòng {@code credit_ledger(ADJUST)} âm, trừ <b>theo FEFO</b>
 *       qua các lô còn hiệu lực, dùng đúng {@code lockLiveBatchesForFefo} mà đường tiêu credit
 *       của người dùng dùng (p5 R2 + §5.6: khoá dòng theo thứ tự {@code expires_at} cố định).</li>
 *   <li>Cả hai chiều đều tính lại entitlement qua {@link EntitlementRecalculationService} — không
 *       làm thì credit vừa cấp <b>không dùng được</b> (xem javadoc lớp đó).</li>
 * </ul>
 *
 * <p><b>Trần an toàn</b> đọc từ {@code app_setting} (p14 §14.4.4 bước 5), mặc định trong mã để
 * app chạy được trên DB chưa seed.</p>
 */
@Service
public class AdminCreditService {

    /** Khoá {@code app_setting} — trần cho MỘT lần điều chỉnh. */
    public static final String KEY_MAX_PER_OPERATION = "credit.admin_adjust_max_per_operation";

    /** Khoá {@code app_setting} — trần MỖI NGÀY cho mỗi admin thao tác. */
    public static final String KEY_MAX_PER_DAY = "credit.admin_adjust_max_per_day";

    /** Mặc định của p14 §14.4.4 bước 5 khi {@code app_setting} chưa seed. */
    public static final int DEFAULT_MAX_PER_OPERATION = 200;

    /** Mặc định của p14 §14.4.4 bước 5 khi {@code app_setting} chưa seed. */
    public static final int DEFAULT_MAX_PER_DAY = 1_000;

    /** p8 §8.1.4: trang mặc định 20 dòng sổ cái. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** p8 §8.1.4: trần cứng 100 dòng/trang. */
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

    /* ------------------------------------------------------------------ L9 */

    /**
     * Lô credit + sổ ledger của một người dùng (p8 L9).
     *
     * <p>Trả <b>tất cả</b> lô, kể cả lô đã {@code EXHAUSTED}/{@code EXPIRED}: p5 R3 ghi rõ lô hết
     * hạn <b>không xoá</b> vì nó là chứng từ đối soát, và câu hỏi tổng đài nhận được gần như luôn
     * là về một lô đã đóng ("credit của tôi đi đâu"). Danh sách lô không phân trang — số lô mỗi
     * tài khoản nhỏ; chỉ sổ ledger phân trang (offset, p8 cột {@code Trang = O}).</p>
     *
     * <p>Ghi {@code audit_log} dù chỉ đọc (cột {@code Aud} của p8 L9, p15 REQ-AUD-04), nên
     * {@code @Transactional} không {@code readOnly}.</p>
     */
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

    /* ----------------------------------------------------------------- L10 */

    /**
     * Điều chỉnh credit thủ công (p8 L10, p14 §14.4.4).
     *
     * <p><b>Idempotency thật, ở tầng DB.</b> {@code Idempotency-Key} của request đi thẳng vào
     * {@code credit_ledger.idempotency_key} ({@code UNIQUE}), và lần gọi lại cùng khoá được phát
     * hiện bằng {@code findByIdempotencyKey} <i>trước</i> khi ghi — không dựa vào ngoại lệ DB.
     * Đây là lớp bảo vệ duy nhất hiện có cho cột {@code Idem = !} của p8: bảng
     * {@code idempotency_record} chung chưa tồn tại (p8 §8.10 OQ-22). Với L10 thì nó đủ, và đủ ở
     * đúng chỗ quan trọng nhất — một lần bấm đúp không được cấp credit hai lần.</p>
     *
     * @param userId         người nhận/bị thu hồi credit
     * @param direction      chiều điều chỉnh
     * @param amount         số credit, luôn dương; dấu do {@code direction} quyết định
     * @param packageCode    gói <b>tham chiếu</b> — snapshot vào {@code credit_batch.package_code}
     *                       (cột NOT NULL, không FK) cho chiều cấp thêm; bỏ qua ở chiều thu hồi
     * @param validityDays   số ngày hiệu lực của lô mới; {@code null} ⇒ lấy
     *                       {@code package_plan.credit_validity_days}
     * @param idempotencyKey header {@code Idempotency-Key}; {@code null} ⇒ không chống replay
     * @param context        ai làm, vì lý do gì
     * @throws BusinessRuleException {@code 422 CREDIT_ADJUST_LIMIT_EXCEEDED} khi vượt trần an toàn
     * @throws ConflictException {@code 409 CREDIT_ADJUST_EXCEEDS_BALANCE} khi thu hồi quá số dư
     * @throws NotFoundException {@code 404 PACKAGE_PLAN_NOT_FOUND} khi gói tham chiếu không tồn tại
     */
    @Transactional
    public AdminCreditAdjustmentResult adjust(UUID userId, AdjustmentDirection direction, int amount,
                                              String packageCode, Integer validityDays,
                                              String idempotencyKey, AdminActionContext context) {
        if (amount <= 0) {
            // Dấu do `direction` quyết định, không do client gửi số âm: hai cách diễn đạt cùng
            // một ý là hai cách để sai (một client gửi direction=REVOKE kèm amount=-50 thì ý
            // nghĩa là cấp thêm hay thu hồi?).
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

        // p14 §14.4.4 bước 8: audit CREDIT_ADJUST kèm reason + before/after SỐ DƯ. before/after là
        // số dư, không phải nội dung lô — đó là con số người dùng sẽ gọi lên hỏi.
        auditLogService.record(base(context, "CREDIT_ADJUST", userId)
                .meta("direction", direction.name())
                .meta("amount", amount)
                // UUID dạng ĐỐI TƯỢNG: `PiiRedactor` thay mọi chuỗi ≥ 32 ký tự
                // [A-Za-z0-9+/=_-] bằng `[REDACTED]_BLOB`, và UUID có dấu gạch ngang khớp đúng lớp
                // đó ⇒ id bị xoá khỏi cột mà p4 §4.6.4 yêu cầu phải có id. Xem handoff H15.156.
                .meta("batchId", result.batchId())
                .before(Map.of("availableBalance", result.balanceBefore()))
                .after(Map.of("availableBalance", result.balanceAfter()))
                .build());

        log.info("Điều chỉnh credit {} {} cho userId={} bởi adminId={} (số dư {} -> {})",
                direction, amount, userId, context.adminId(),
                result.balanceBefore(), result.balanceAfter());
        return result;
    }

    /* --------------------------------------------------------------- nội bộ */

    private AdminCreditAdjustmentResult grant(UUID userId, int amount, String packageCode,
                                               Integer validityDays, String idempotencyKey,
                                               UUID adminId, Instant now) {
        // `credit_batch.package_code` là NOT NULL và không có FK (snapshot, p5 R1) — nên gói tham
        // chiếu là BẮT BUỘC dù p14 bước 3 cho phép "nhập tự do": tự do ở đây là số lượng và số
        // ngày, không phải quyền tạo một lô không thuộc gói nào.
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

        // Số dư đọc SAU khi lô đã vào DB, giống ActivateCreditCodeService: `balance_after` phải
        // phản ánh trạng thái sau giao dịch (p5 R4).
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

        // FEFO cũng cho chiều thu hồi (p8 L10: "ADJUST âm khi thu hồi theo FEFO"). Thu hồi từ lô
        // sắp hết hạn trước là hướng có lợi cho người dùng: phần credit còn lại của họ có hạn dài
        // hơn.
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

        // Khoá idempotency là UNIQUE MỘT cột (p4 §4.6.1) nên chỉ dòng ĐẦU mang khoá; các dòng sau
        // của cùng lần thu hồi để NULL — cùng quy ước mà đường CONSUME của FEFO đang dùng.
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

    /**
     * Trần an toàn p14 §14.4.4 bước 5: một lần và một ngày cho mỗi admin.
     *
     * <p>"Một ngày" tính từ <b>đầu ngày UTC</b>, không phải "24 giờ gần nhất": trần theo cửa sổ
     * trôi sẽ làm hạn mức mở lại nhỏ giọt suốt đêm, còn mốc đầu ngày thì cả admin lẫn người đối
     * soát đều nói được "hôm nay đã dùng bao nhiêu". Chọn UTC vì {@code credit_ledger.created_at}
     * là {@code TIMESTAMPTZ} và server không biết múi giờ của người đang thao tác.</p>
     */
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

    /**
     * Dựng lại kết quả của lần gọi trước khi {@code Idempotency-Key} đã dùng (p8 §8.1.8: trả
     * nguyên response cũ, <b>không</b> thực hiện lại side effect).
     *
     * <p>Chỉ dựng lại được phần quan sát được từ một dòng ledger — số dư sau giao dịch là chính
     * xác, còn {@code balanceBefore} suy ra bằng {@code balanceAfter - amount} của dòng mang khoá.
     * Với chiều thu hồi vắt qua nhiều lô, con số đó là của lô đầu; chấp nhận được vì replay chỉ
     * cần nói "đã làm rồi, đây là kết quả" chứ không phải dựng lại toàn bộ chứng từ.</p>
     */
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

    /**
     * <b>{@code credit_ledger.ref_id} của dòng điều chỉnh = id của admin thao tác.</b>
     *
     * <p>p14 §14.4.4 bước 8 ghi {@code ref_id = <audit_log.id>}, nhưng {@code AuditLogService}
     * chỉ có {@code void record(...)} — không trả id, và module {@code audit} nằm ngoài phạm vi
     * sở hữu của gói việc này. Id admin còn là thứ <i>duy nhất</i> làm được trần
     * "{@code maxPerDay} mỗi admin" bằng một câu SQL trên chính {@code credit_ledger}
     * ({@link CreditLedgerPort#sumAdminAdjustedAbsSince}) — với {@code audit_log.id} thì trần đó
     * buộc phải join sang bảng khác. Xem handoff H15.154.</p>
     *
     * <p>{@code credit_ledger.note} bắt buộc NOT NULL/không rỗng cho {@code ADJUST}
     * ({@code ck_credit_ledger_adjust_note}, p4 §4.4.6).
     *
     * <p>Ghi một câu cố định chứ KHÔNG ghi {@code reason} của admin: {@code note} là cột mà
     * <b>người dùng</b> đọc ở {@code GET /credits/ledger} (p8 H3), còn {@code reason} là văn bản
     * nội bộ có thể chứa số ticket, tên nhân viên, chi tiết sự cố. p14 §14.4.4 bước 9 nói thẳng:
     * người dùng thấy nguồn gốc "Cấp bởi CatCheck", <b>không lộ danh tính admin</b>. Toàn văn
     * {@code reason} nằm ở {@code audit_log} (bước 8) và chỉ người đủ quyền đọc được.</p>
     */
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

    /** Chiều điều chỉnh — p14 §14.4.4 bước 2 ("Cấp thêm" / "Thu hồi"). */
    public enum AdjustmentDirection {

        /** Cấp thêm ⇒ lô {@code credit_batch} mới + dòng {@code GRANT} (p14 bước 8). */
        GRANT,

        /** Thu hồi ⇒ các dòng {@code ADJUST} âm, trừ theo FEFO (p8 L10). */
        REVOKE
    }

    /**
     * Dữ liệu L9.
     *
     * @param availableBalance   số dư khả dụng hiện tại (p5 R4)
     * @param batches            tất cả lô, kể cả đã đóng (chứng từ đối soát, p5 R3)
     * @param ledger             một trang sổ cái, mới nhất trước
     * @param ledgerTotal        tổng số dòng sổ cái
     * @param ledgerPage         số trang, đếm từ 0
     * @param ledgerSize         kích thước trang đã kẹp về {@code [1, 100]}
     */
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

    /** Một lô bị biến động trong lần điều chỉnh. */
    public record BatchDelta(UUID batchId, int amount) {
    }

    /**
     * Kết quả L10.
     *
     * @param direction     chiều đã thực hiện
     * @param amount        số credit (luôn dương)
     * @param balanceBefore số dư khả dụng trước giao dịch
     * @param balanceAfter  số dư khả dụng sau giao dịch
     * @param batchId       lô mới tạo (chiều cấp thêm), {@code null} ở chiều thu hồi
     * @param expiresAt     hạn của lô mới, {@code null} ở chiều thu hồi
     * @param batches       chi tiết từng lô bị biến động
     * @param replayed      {@code true} khi đây là replay của một {@code Idempotency-Key} đã dùng
     */
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
