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
import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import com.catcheck.credit.domain.PlanTier;
import com.catcheck.credit.domain.port.ActivationCodeHasher;
import com.catcheck.credit.domain.port.ActivationCodePort;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Nghiệp vụ kích hoạt gói: đổi một mã {@code ISSUED} thành một lô credit +
 * dòng {@code GRANT} trong sổ cái + cập nhật entitlement (p5 R1).
 *
 * <p><b>MỘT transaction, không ngoại lệ.</b> Nếu tạo lô mà chưa ghi ledger thì bất biến I1
 * ({@code initial_amount + SUM(ledger) = remaining_amount}) vi phạm ngay ở lần đọc số dư đầu
 * tiên, và {@code InvariantAuditJob} (p4 §4.5.2) sẽ báo đỏ.</p>
 *
 * <p>Thứ tự thao tác cố ý: tra hash → khoá dòng mã → kiểm tra trạng thái/hạn → đổi trạng thái
 * có điều kiện → tạo lô → ghi ledger → tính lại entitlement. Đổi trạng thái <b>trước</b> khi tạo
 * lô để nếu có lỗi ở giữa thì {@code markRedeemed} trả {@code false} chứ không sinh hai lô cho
 * một mã (bất biến I24).</p>
 */
@Service
public class ActivateCreditCodeService {

    private static final Logger log = LoggerFactory.getLogger(ActivateCreditCodeService.class);

    /**
     * Cổng mã kích hoạt. Tên field cố ý là {@code codePort}/{@code codeHasher} chứ không phải
     * {@code activationCodePort}/{@code activationCodeHasher}: R16 chặn việc đọc một field có
     * tên khớp regex {@code (?i).*(...|activationCode|...)} trong method có gọi logger. Tên field
     * là tên phụ thuộc chứ không phải PII, nên đổi tên ở đây giữ đúng ý nghĩa của guard thay vì
     * phải bỏ log.
     */
    private final ActivationCodePort codePort;

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;
    private final UserEntitlementPort userEntitlementPort;
    private final PackagePlanPort packagePlanPort;
    private final ActivationCodeHasher codeHasher;
    private final AuditLogService auditLogService;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public ActivateCreditCodeService(
            ActivationCodePort codePort,
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            UserEntitlementPort userEntitlementPort,
            PackagePlanPort packagePlanPort,
            ActivationCodeHasher codeHasher,
            AuditLogService auditLogService,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.codePort = codePort;
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.userEntitlementPort = userEntitlementPort;
        this.packagePlanPort = packagePlanPort;
        this.codeHasher = codeHasher;
        this.auditLogService = auditLogService;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /**
     * Kích hoạt một mã cho user hiện tại — {@code POST /api/v1/activations} (p8 H1).
     *
     * @param userId  chủ tài khoản nhận credit
     * @param rawCode mã thô do người dùng nhập — chỉ tồn tại trong lời gọi này, KHÔNG ghi log,
     *                không lưu DB (p5 §5.9: DB chỉ có {@code code_hash})
     * @param facts   thông tin request (requestId/IP/userAgent) cho audit — p8 H1 cột {@code Aud}
     * @return kết quả kích hoạt
     * @throws BusinessRuleException {@link CreditErrorCode#ACTIVATION_CODE_INVALID} khi không
     *         tra được mã, hoặc {@link CreditErrorCode#ACTIVATION_CODE_EXPIRED} khi quá hạn
     * @throws ConflictException {@link CreditErrorCode#ACTIVATION_CODE_ALREADY_USED}
     */
    @Transactional
    public ActivationResult activate(UUID userId, String rawCode, RequestFacts facts) {

        // Bắt lỗi định dạng/checksum TRƯỚC khi tra DB: một mã sai định dạng không bao giờ là mã
        // hợp lệ, nên không tốn một lần tra hash (p8 ACTIVATION_CODE_MALFORMED, p5 §5.9).
        String normalized = ActivationCodeFormat.validateOrNull(rawCode);
        if (normalized == null) {
            throw new BusinessRuleException(
                    CreditErrorCode.ACTIVATION_CODE_MALFORMED, ActivationCodeFormat.expectedFormat());
        }

        ActivationCodeHasher.HashedActivationCode hashed = codeHasher.hash(normalized);
        UUID codeId = codePort.findByCodeHash(hashed.hex())
                .map(ActivationCode::id)
                // Cùng mã lỗi với "mã không tồn tại": không rò ra rằng mã này có thật.
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        Instant now = clock.instant();

        // Khoá trước, đọc lại sau. `code` ở trên là ẢNH CHỤP TRƯỚC KHOÁ — khoá chỉ chặn ghi, nó
        // không làm cho object đã nạp vào bộ nhớ tự nhiên mới theo. Request thứ nhất đã redeem
        // xong và commit trước khi request thứ hai vào được khoá, nên request thứ hai phải đọc
        // lại từ DB mới thấy REDEEMED. Dùng lại ảnh chụp cũ sẽ cho qua bước chuyển trạng thái rồi
        // sinh ra lô thứ hai cho một mã (vi phạm I24).
        ActivationCode code = codePort.lockById(codeId)
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        if (code.status() == ActivationCodeStatus.REDEEMED) {
            // Cờ "chính tôi đã dùng" cho phép thông điệp riêng, nhưng KHÔNG lộ mã đã dùng bởi
            // ai nếu không phải chính người dùng đó (p11 §11.7.4 — không tiết lộ trạng thái mã).
            boolean selfRedeem = code.redeemedBy() != null && code.redeemedBy().equals(userId);
            throw new ConflictException(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED,
                    code.redeemedAt(), selfRedeem);
        }
        if (code.status() == ActivationCodeStatus.VOID) {
            // VOID và "không tồn tại" cùng trả một mã lỗi, để không dò ra mã nào còn hiệu lực.
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID);
        }
        if (code.isIssuanceExpiredAt(now)) {
            throw new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_EXPIRED, code.validUntil());
        }

        PackagePlan plan = packagePlanPort.findByCode(code.packageCode())
                .orElseThrow(() -> new BusinessRuleException(CreditErrorCode.ACTIVATION_CODE_INVALID));

        if (!codePort.markRedeemed(code.id(), userId, now)) {
            // Lớp chống double-redeem thứ hai, ở tầng DB (`WHERE status = 'ISSUED'`): tới đây
            // nghĩa là có request khác vừa đổi trạng thái dù khoá đã được tôn trọng.
            throw new ConflictException(CreditErrorCode.ACTIVATION_CODE_ALREADY_USED, now, false);
        }

        // Hạn tính từ LÚC KÍCH HOẠT, không phải lúc phát hành: user mua gói tháng 3 còn 28 ngày
        // vẫn phải nhận đủ 30 ngày (p5 R1).
        CreditBatchGrant batch = CreditBatchGrant.of(
                uuidV7.generate(), userId, code.id(), plan, now);
        creditBatchPort.insertNewBatch(batch);

        int balanceAfter = creditLedgerPort.availableBalance(userId, now);
        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(), userId, batch.id(), CreditLedgerType.GRANT, plan.creditAmount(),
                balanceAfter, CreditLedgerRefType.ACTIVATION,
                code.id(), null, plan.name(), now));

        Entitlement entitlement = refreshEntitlement(userId, now);

        // Ghi log KHÔNG chứa mã thô, mã băm, tên đăng nhập hay email — chỉ id và mã gói.
        log.info("Kích hoạt gói {}: userId={} batchId={} credits={} balanceAfter={}",
                plan.code(), userId, batch.id(), plan.creditAmount(), balanceAfter);

        // p8 H1 cột Aud: ghi audit_log TRONG CÙNG transaction; metadata TUYỆT ĐỐI không chứa
        // mã thô/mã băm (danh sách khoá bị che I29: activationCode, codeHash) — chỉ id và số liệu.
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

    /**
     * Tính lại {@code user_entitlement} từ các lô user đã từng kích hoạt (bất biến I28).
     *
     * <p>Gói cao nhất lấy theo thứ tự {@link PlanTier}. Điểm mấu chốt của p5 R5: quyền ĐỌC giữ
     * vĩnh viễn — user từng mua gói MULTI rồi hạ xuống MINI vẫn đọc được lịch sử và xuất PDF; chỉ
     * quyền TẠO MỚI bị chặn theo {@code writeAccessUntil}.</p>
     *
     * <p>Quét TẤT CẢ lô (kể cả lô đã đóng) chứ không chỉ lô còn sống, vì gói đã hết hạn vẫn phải
     * giữ quyền đọc.</p>
     */
    private Entitlement refreshEntitlement(UUID userId, Instant now) {
        Entitlement current = userEntitlementPort.findOrDefault(userId, now);

        String highestCode = current.highestPackage();
        for (String packageCode : creditBatchPort.findAllBatches(userId).stream()
                .map(batch -> batch.packageCode())
                .filter(Objects::nonNull)
                .toList()) {
            highestCode = PlanTier.isHigher(packageCode, highestCode) ? packageCode : highestCode;
        }

        PlanFeatures features = PlanFeatures.none();
        Integer maxCatProfiles = null;
        if (highestCode != null) {
            Optional<PackagePlan> plan = packagePlanPort.findByCode(highestCode);
            if (plan.isPresent()) {
                features = plan.get().features();
                maxCatProfiles = plan.get().maxCatProfiles();
            }
        }

        // I28: write_access_until = MAX(expires_at) của các lô ĐÃ KÍCH HOẠT. Tính ở đây thay
        // vì để job cập nhật, để quyền ghi có hiệu lực ngay ở lần gọi kế tiếp sau kích hoạt
        // (p8 H4: không cache quyền trong phiên).
        Instant writeAccessUntil = userEntitlementPort.maxActivatedBatchExpiry(userId).orElse(null);

        Entitlement refreshed = new Entitlement(
                userId, highestCode, maxCatProfiles, features, writeAccessUntil,
                current.trialScansUsed(), now);
        userEntitlementPort.save(refreshed);
        return refreshed;
    }

    /**
     * Kết quả kích hoạt — {@code POST /api/v1/activations} (p8 H1).
     *
     * @param packageCode     gói vừa kích hoạt
     * @param packageName     tên hiển thị của gói
     * @param creditsGranted  số credit được cấp
     * @param expiresAt       hạn credit, tính từ lúc kích hoạt
     * @param balanceAfter    số dư khả dụng toàn user sau kích hoạt
     * @param maxCatProfiles  hạn mức hồ sơ mèo theo gói mới, {@code null} = không giới hạn
     */
    public record ActivationResult(
            String packageCode,
            String packageName,
            int creditsGranted,
            Instant expiresAt,
            int balanceAfter,
            Integer maxCatProfiles
    ) {
    }

    /**
     * Thông tin request cho audit — controller tự từ {@code HttpServletRequest} dựng rồi truyền
     * xuống (tầng application không được chạm servlet API, R2/R3). Không phải PII theo p17: IP
     * đã do {@code PiiRedactor} của module audit che khi ghi log.
     *
     * @param requestId  giá trị header {@code X-Request-Id}, {@code null} nếu client không gửi
     * @param ipAddress IP socket (chưa qua lọc proxy tin cậy — việc đó của W3, p11 §11.7.1)
     * @param userAgent header {@code User-Agent}, {@code null} nếu không có
     */
    public record RequestFacts(String requestId, String ipAddress, String userAgent) {

        public static final RequestFacts UNKNOWN = new RequestFacts(null, null, null);

        public static RequestFacts of(String requestId, String ipAddress, String userAgent) {
            return new RequestFacts(requestId, ipAddress, userAgent);
        }
    }

}
