package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.shared.security.PiiMask;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AdminUserQueryPort;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tra cứu và hỗ trợ tài khoản người dùng từ màn quản trị — L1, L2, L3, L7, L8, L11, L12
 * (p8 §8.4.12 mục (a); màn ở p14 §14.3.2 mục 2).
 *
 * <p>Mọi phương thức ở đây ghi {@code audit_log}, kể cả ĐỌC: p14 cột {@code Aud} bật cho cả L1
 * và L2, và p15 REQ-AUD-04 cho phép chính người dùng xem lại ở {@code /account/privacy} ai đã
 * xem gì của mình. Đó là lý do một endpoint chỉ đọc vẫn {@code @Transactional} (không
 * {@code readOnly}).</p>
 *
 * <p><b>Mask:</b> PII rời khỏi lớp này đã mask (p15 REQ-RBAC-01) — trừ {@link #unmask} là hành
 * động tường minh, có {@code reason}, có audit. Lớp này KHÔNG log email/phone (R16, p15
 * REQ-SEC-03); mọi dòng log chỉ có {@code userId}.</p>
 */
@Service
public class AdminUserService {

    /**
     * p15 REQ-RBAC-01: bỏ mask <b>tự khôi phục sau 15 phút</b>. Giá trị này hiện chỉ đi vào
     * response ({@code unmaskedUntil}) để UI tự che lại và vào {@code audit_log}; server chưa
     * giữ trạng thái "đang unmask" nào — xem {@link #unmask} và handoff H15.104.
     */
    public static final Duration UNMASK_WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final String PHONE_TABLE = "app_user";
    private static final String PHONE_COLUMN = "phone";

    private final AdminUserQueryPort userQueryPort;
    private final UserAccountRepository accountRepository;
    private final UserRoleRepository roleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final MfaTotpRepository totpRepository;
    private final PiiCipher piiCipher;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminUserService(AdminUserQueryPort userQueryPort,
                            UserAccountRepository accountRepository,
                            UserRoleRepository roleRepository,
                            DeviceSessionRepository sessionRepository,
                            MfaTotpRepository totpRepository,
                            PiiCipher piiCipher,
                            AuditLogService auditLogService,
                            Clock clock) {
        this.userQueryPort = userQueryPort;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
        this.totpRepository = totpRepository;
        this.piiCipher = piiCipher;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ L1 */

    @Transactional
    public Page<AdminUserSummary> search(UserStatus status, String email, String highestPackage,
                                         int page, int size, AdminActionContext context) {
        List<AdminUserSummary> items =
                userQueryPort.search(status, email, highestPackage, page * size, size);
        long total = userQueryPort.count(status, email, highestPackage);

        // Ghi MỘT dòng cho cả lần tra cứu, không một dòng mỗi user: p11 §11.11.1 ghi lại HÀNH
        // ĐỘNG, và hành động ở đây là "đã mở danh sách với bộ lọc X", không phải "đã xem N
        // người". Ghi N dòng còn làm một lần phân trang biến thành N bản ghi nhiễu.
        auditLogService.record(base(context, "ADMIN_USER_SEARCH", null)
                .meta("status", status == null ? null : status.name())
                .meta("byEmail", email != null && !email.isBlank())
                .meta("highestPackage", highestPackage)
                .meta("resultCount", items.size())
                .build());
        return new Page<>(items, total);
    }

    /* ------------------------------------------------------------------ L2 */

    @Transactional
    public AdminUserDetail detail(UUID userId, AdminActionContext context) {
        AdminUserSummary summary = requireSummary(userId);
        UserAccount account = requireAccount(userId);
        Instant now = clock.instant();
        List<UserRole> roles = roleRepository.findByUserId(userId);
        int activeSessions = sessionRepository.findActiveByUserId(userId, now).size();
        boolean totpEnabled = totpRepository.findByUserId(userId)
                .map(totp -> totp.isActive())
                .orElse(false);

        auditLogService.record(base(context, "ADMIN_USER_VIEW", userId).build());

        // Giải mã số điện thoại rồi mask NGAY tại đây: bản thô không được ra khỏi phương thức
        // này (đường duy nhất cho bản thô là unmask(), có reason + audit). Giải mã chỉ để mask
        // nghe như làm thừa, nhưng cột `phone` là ciphertext nên không có cách nào biết nó có
        // bao nhiêu chữ số mà không giải mã (p4 §A1: không index được, không LIKE được).
        return new AdminUserDetail(summary, account, roles, activeSessions, totpEnabled,
                PiiMask.phone(decryptPhone(account)));
    }

    /* ------------------------------------------------------------------ L3 */

    /**
     * Bỏ mask email/phone — {@code action = ADMIN.PII_UNMASKED} (p11 §11.11.4, p15 REQ-RBAC-01).
     *
     * <p><b>Cài đặt tối thiểu, đã ghi handoff H15.104:</b> trả giá trị thật đúng một lần trong
     * response của chính lần POST này, kèm {@code unmaskedUntil = now + 15 phút} để UI tự che
     * lại. Server KHÔNG lưu trạng thái "phiên này đang unmask user X" vì p4 không có bảng nào
     * cho nó (và CLAUDE.md cấm thêm migration ngoài danh mục p4 §4.9.2) — nên L2 sau đó vẫn trả
     * bản mask, và muốn xem tiếp thì phải bấm unmask lần nữa, mỗi lần một dòng audit. So với
     * đặc tả thì <b>chặt hơn</b> về số lần ghi audit và <b>lỏng hơn</b> ở chỗ UI phải tự tôn
     * trọng cửa sổ 15 phút; test AD12 của p17 §17.3.8 chưa làm được cho tới khi có nơi lưu.</p>
     */
    @Transactional
    public UnmaskedPii unmask(UUID userId, AdminActionContext context) {
        UserAccount account = requireAccount(userId);
        String phone = decryptPhone(account);

        auditLogService.record(base(context, "ADMIN_PII_UNMASKED", userId)
                // `fields[]` theo p11 §11.11.4. Chỉ TÊN trường, không bao giờ giá trị.
                .meta("fields", phone == null ? List.of("email") : List.of("email", "phone"))
                .meta("windowMinutes", UNMASK_WINDOW.toMinutes())
                .build());

        log.info("Admin bỏ mask PII: actor={} subject={}", context.adminId(), userId);
        return new UnmaskedPii(
                userId, account.email().value(), phone, clock.instant().plus(UNMASK_WINDOW));
    }

    /* ---------------------------------------------------------------- L7/L8 */

    /**
     * Khoá tài khoản ⇒ {@code status = LOCKED} + <b>thu hồi mọi phiên ngay</b> (p8 L7,
     * p11 §11.1.8).
     *
     * <p>{@code locked_until = null} là cố ý: {@code locked_until} là khoá TẠM do đăng nhập sai
     * nhiều lần và tự hết hạn (p11 §11.1.9), còn khoá của admin phải đứng đến khi có người mở.
     * {@code LoginService} từ chối theo {@code status == LOCKED} độc lập với
     * {@code locked_until}, nên không có đường nào tự mở.</p>
     *
     * @throws ConflictException {@code 409 ADMIN_CANNOT_MODIFY_SELF} nếu admin tự khoá mình
     *                           (p8 §8.2.4 — tự khoá là cách nhanh nhất để mất khu vực quản trị)
     */
    @Transactional
    public LockResult lock(UUID userId, AdminActionContext context) {
        requireNotSelf(userId, context);
        UserAccount account = requireAccount(userId);
        if (account.status() == UserStatus.ANONYMIZED
                || account.status() == UserStatus.DELETION_REQUESTED
                || account.status() == UserStatus.RESTRICTED) {
            // Ba trạng thái này gắn với cột bắt buộc khác NULL (CHECK ck_app_user_restricted_pair
            // / ck_app_user_deletion_schedule). Đổi sang LOCKED sẽ vi phạm CHECK và trả 500.
            throw new ConflictException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        Instant now = clock.instant();
        accountRepository.updateLoginState(
                userId, UserStatus.LOCKED, account.failedLoginCount(), null, account.lastLoginAt());
        int revoked = sessionRepository.revokeAllByUserId(userId, now, SessionRevokeReason.ADMIN_LOCK);

        auditLogService.record(base(context, "ADMIN_LOCK_ACCOUNT", userId)
                .before(java.util.Map.of("status", account.status().name()))
                .after(java.util.Map.of("status", UserStatus.LOCKED.name()))
                .meta("revokedSessions", revoked)
                .build());

        log.info("Admin khoá tài khoản: actor={} subject={} revokedSessions={}",
                context.adminId(), userId, revoked);
        return new LockResult(userId, UserStatus.LOCKED, revoked);
    }

    /** Mở khoá — KHÔNG tạo lại phiên nào (p8 L8). */
    @Transactional
    public LockResult unlock(UUID userId, AdminActionContext context) {
        UserAccount account = requireAccount(userId);
        if (account.status() != UserStatus.LOCKED) {
            throw new ConflictException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        accountRepository.updateLoginState(
                userId, UserStatus.ACTIVE, 0, null, account.lastLoginAt());

        auditLogService.record(base(context, "ADMIN_UNLOCK_ACCOUNT", userId)
                .before(java.util.Map.of("status", UserStatus.LOCKED.name()))
                .after(java.util.Map.of("status", UserStatus.ACTIVE.name()))
                .build());

        log.info("Admin mở khoá tài khoản: actor={} subject={}", context.adminId(), userId);
        return new LockResult(userId, UserStatus.ACTIVE, 0);
    }

    /* -------------------------------------------------------------- L11/L12 */

    @Transactional
    public List<DeviceSession> sessions(UUID userId, AdminActionContext context) {
        requireSummary(userId);
        List<DeviceSession> sessions = sessionRepository.findActiveByUserId(userId, clock.instant());
        auditLogService.record(base(context, "ADMIN_USER_SESSIONS_VIEW", userId)
                .meta("sessionCount", sessions.size())
                .build());
        return sessions;
    }

    /** Thu hồi mọi phiên của một user (L12) — dùng khi user báo bị chiếm tài khoản. */
    @Transactional
    public int revokeAllSessions(UUID userId, AdminActionContext context) {
        requireSummary(userId);
        int revoked = sessionRepository.revokeAllByUserId(
                userId, clock.instant(), SessionRevokeReason.USER_REVOKE_ALL);
        auditLogService.record(base(context, "ADMIN_USER_SESSIONS_REVOKE", userId)
                .meta("revokedSessions", revoked)
                .build());
        log.info("Admin thu hồi phiên: actor={} subject={} revokedSessions={}",
                context.adminId(), userId, revoked);
        return revoked;
    }

    /* ---------------------------------------------------------------- helper */

    private AuditEvent.Builder base(AdminActionContext context, String action, UUID subjectUserId) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(context.auditActor())
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent());
        if (subjectUserId != null) {
            builder.subjectUser(subjectUserId);
        }
        if (context.reason() != null) {
            builder.meta("reason", context.reason());
        }
        return builder;
    }

    private void requireNotSelf(UUID userId, AdminActionContext context) {
        if (userId.equals(context.adminId())) {
            throw new ConflictException(IdentityErrorCode.ADMIN_CANNOT_MODIFY_SELF);
        }
    }

    private AdminUserSummary requireSummary(UUID userId) {
        return userQueryPort.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
    }

    private UserAccount requireAccount(UUID userId) {
        return accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
    }

    private String decryptPhone(UserAccount account) {
        if (account.phone() == null) {
            return null;
        }
        byte[] plaintext = piiCipher.decrypt(CryptoPurpose.PII_PHONE,
                PiiCipher.aad(PHONE_TABLE, PHONE_COLUMN, account.id()),
                account.phone());
        return plaintext == null ? null : new String(plaintext, StandardCharsets.UTF_8);
    }

    /** Chi tiết L2 — gom bốn nguồn để controller không phải gọi bốn service. */
    public record AdminUserDetail(
            AdminUserSummary summary,
            UserAccount account,
            List<UserRole> roles,
            int activeSessionCount,
            boolean totpEnabled,
            String phoneMasked) {
    }

    /** Giá trị thật, trả đúng một lần. KHÔNG log, KHÔNG cache, KHÔNG ghi audit giá trị. */
    public record UnmaskedPii(UUID userId, String email, String phone, Instant unmaskedUntil) {
    }

    public record LockResult(UUID userId, UserStatus status, int revokedSessions) {
    }

    /** Một trang kết quả admin — xem javadoc cùng tên ở {@code credit.application}. */
    public record Page<T>(List<T> items, long totalElements) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /** Actor + lý do + dấu vết request cho một hành động admin của module identity. */
    public record AdminActionContext(
            UUID adminId,
            String adminRole,
            String reason,
            String requestId,
            String ipAddress,
            String userAgent) {

        public AdminActionContext {
            if (adminId == null) {
                throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
            }
        }

        /**
         * {@code actor_type}: {@code 'DPO'} khi vai trò đã dùng là DPO, còn lại {@code 'ADMIN'}.
         *
         * <p>Phân biệt vì {@code audit_log} là nơi chứng minh tuân thủ: L55/L6 chỉ DPO được gọi,
         * và một bản ghi {@code actor_type = 'ADMIN'} cho hành động chỉ-DPO sẽ không chứng minh
         * được gì khi thanh tra (p4 §4.4.3 có sẵn giá trị {@code 'DPO'} cho đúng việc này).</p>
         */
        public AuditActor auditActor() {
            return "DPO".equals(adminRole)
                    ? AuditActor.dpo(adminId, adminRole)
                    : AuditActor.admin(adminId, adminRole);
        }
    }
}
