package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.RetentionPolicy;
import com.catcheck.privacy.domain.port.RetentionPolicyPort;
import com.catcheck.privacy.domain.port.RetentionDryRunPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

/**
 * Quản lý cấu hình thời hạn lưu trữ (p4 B6, p15 REQ-RET-03): thời hạn KHÔNG hard-code
 * để DPO/luật sư chỉnh mà không cần deploy. Chỉ role {@code DPO} được sửa (REQ-RET-06).
 *
 * <p>Bất biến I15 (p4 §4.5.1): hai giá trị KHÔNG được NỚI bằng bảng này vì là cam kết
 * với owner/user — {@code SCAN_IMAGE ≤ 14 ngày} (quyết định #9) và grace xoá tài khoản
 * ≤ 7 ngày (TD-05). Service chặn giá trị lớn hơn kèm lý do.</p>
 */
@Service
public class RetentionService {

    /** Quyết định owner #9: ảnh scan tối đa 14 ngày — không được nới bằng cấu hình (I15). */
    public static final int SCAN_IMAGE_MAX_DAYS = 14;

    /** TD-05 / p15 §15.4.6: grace xoá tài khoản tối đa 7 ngày — nửa thứ hai của I15. */
    public static final int ACCOUNT_DELETION_GRACE_MAX_DAYS = 7;

    /**
     * {@code retention_policy.code} mang trần cứng I15, khớp theo <b>tiền tố</b>.
     *
     * <p><b>Bug thật đã sửa, phát hiện bằng curl chứ không bằng suy đoán:</b> bản trước so
     * {@code "SCAN_IMAGE".equals(policy.code())}, nhưng mã thật trong seed
     * ({@code db/seed/R__seed_retention_policy.sql}) là <b>{@code SCAN_IMAGE_RAW}</b> — p4 B6
     * chỉ ghi {@code SCAN_IMAGE} làm ví dụ. Nghĩa là trần cứng 14 ngày của quyết định owner #9
     * <b>chưa bao giờ chạm được dòng nào</b>: một DPO đặt {@code SCAN_IMAGE_RAW = 365} sẽ
     * được lưu, và cam kết "ảnh xoá sau 14 ngày" đã in trong copy hiển thị cho user trở thành
     * sai mà không ai bị chặn. Xác nhận bằng {@code GET /admin/privacy/retention-policies}
     * trên DB thật: dòng duy nhất cho ảnh scan có {@code code = 'SCAN_IMAGE_RAW'}.</p>
     *
     * <p>{@code SCAN_ANALYSIS_LIFECYCLE} (cũng bắt đầu bằng {@code SCAN_}) cố ý KHÔNG bị chặn:
     * nó là số đo đã khử nhận dạng, không phải ảnh.</p>
     */
    private static final List<String> SCAN_IMAGE_CODE_PREFIXES = List.of("SCAN_IMAGE");
    private static final List<String> DELETION_GRACE_CODE_PREFIXES =
            List.of("ACCOUNT_DELETION", "ACCOUNT_ERASE", "DELETION_GRACE");

    /** Vai trò được sửa retention_policy (p11 §11.5.1). */
    private static final String ROLE_DPO = "DPO";

    private final RetentionPolicyPort policyPort;
    private final UserAccountPort userAccountPort;
    private final RetentionDryRunPort dryRunPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public RetentionService(
            RetentionPolicyPort policyPort,
            UserAccountPort userAccountPort,
            RetentionDryRunPort dryRunPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.policyPort = policyPort;
        this.userAccountPort = userAccountPort;
        this.dryRunPort = dryRunPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** Cấu hình retention hiện có — nguồn dữ liệu cho dashboard {@code /admin/privacy/retention} (p15 REQ-RET-05, M6). */
    public List<RetentionPolicy> listPolicies() {
        return policyPort.findAll();
    }

    /**
     * Thêm/cập nhật chính sách retention. Chỉ DPO (p11 §15.5.2 REQ-RET-06); giá trị nới
     * quá cam kết owner thì từ chối kèm lý do (I15).
     */
    public void savePolicy(RetentionPolicy policy, UUID actor) {
        UserAccountSnapshot snapshot = userAccountPort.snapshot(actor);
        if (!snapshot.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        requireWithinHardCap(policy);
        policyPort.save(policy);
    }

    /**
     * Bất biến I15 (p4 §4.5.1): hai thời hạn KHÔNG được nới bằng bảng cấu hình vì chúng là
     * cam kết đã công bố — 14 ngày ảnh scan (quyết định owner #9, đã in vào copy hiển thị cho
     * user) và 7 ngày grace xoá tài khoản (TD-05, là điều kiện để hoàn tất xoá trong hạn luật
     * định 20 ngày của p15 §15.4.6).
     *
     * <p>Mã lỗi là {@code 422 RETENTION_LIMIT_EXCEEDED} theo đúng ô L57 của p8 §8.4.12 —
     * trước đây chỗ này trả {@code 400 VALIDATION_FAILED}, nghĩa là client không phân biệt
     * được "gõ sai kiểu" với "giá trị bị luật chặn".</p>
     */
    private static void requireWithinHardCap(RetentionPolicy policy) {
        if (policy.retentionDays() == null) {
            return;
        }
        Integer cap = hardCapFor(policy.code());
        if (cap != null && policy.retentionDays() > cap) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.RETENTION_LIMIT_EXCEEDED, policy.code(), cap);
        }
    }

    /** Trần cứng của một mã chính sách, {@code null} nếu mã đó không nằm trong I15. */
    public static Integer hardCapFor(String code) {
        if (code == null) {
            return null;
        }
        String normalised = code.strip().toUpperCase(java.util.Locale.ROOT);
        if (SCAN_IMAGE_CODE_PREFIXES.stream().anyMatch(normalised::startsWith)) {
            return SCAN_IMAGE_MAX_DAYS;
        }
        return DELETION_GRACE_CODE_PREFIXES.stream().anyMatch(normalised::startsWith)
                ? ACCOUNT_DELETION_GRACE_MAX_DAYS
                : null;
    }

    /** L58 — chỉ đếm bản ghi có thể hết hạn, tuyệt đối không gọi executor xoá/ẩn danh. */
    public DryRunResult dryRun(String code, UUID actor, String actorRole, RequestEvidence evidence) {
        RetentionPolicy policy = policyPort.findByCode(code)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.RETENTION_POLICY_NOT_FOUND, code));
        Instant now = clock.instant();
        Instant cutoff = policy.retentionDays() == null
                ? now
                : now.minusSeconds(policy.retentionDays() * 86_400L);
        long candidateCount = policy.retentionDays() == null
                ? 0
                : dryRunPort.countExpired(policy, cutoff);
        auditLogService.record(AuditEvent.builder()
                .actor("DPO".equals(actorRole) ? AuditActor.dpo(actor, actorRole) : AuditActor.admin(actor, actorRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("RETENTION_DRY_RUN")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("policyCode", policy.code())
                .meta("targetTable", policy.targetTable())
                .meta("candidateCount", candidateCount)
                .meta("cutoff", cutoff)
                .build());
        return new DryRunResult(policy.code(), policy.targetTable(), policy.anchorColumn(),
                policy.retentionDays(), cutoff, candidateCount, policy.actionOnExpiry().name());
    }

    public record DryRunResult(
            String policyCode,
            String targetTable,
            String anchorColumn,
            Integer retentionDays,
            Instant cutoff,
            long candidateCount,
            String actionOnExpiry) {
    }
}
