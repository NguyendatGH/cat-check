package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * L53 — <b>phê duyệt và thực thi</b> xoá tài khoản theo quy tắc hai người
 * (p8 §8.4.12 ô L53, p14 §14.2.2 ô Q9, p15 REQ-RBAC-03:
 * <i>"ADMIN đề xuất → DPO duyệt"</i>).
 *
 * <p><b>Ba lớp của quy tắc hai người, theo đúng thứ tự kiểm:</b></p>
 * <ol>
 *   <li><b>Chỉ {@code DPO} duyệt.</b> p8 ghi {@code R:DPO} và p14 Q9 để {@code ADMIN_SUPER}
 *       là ❌ — nên một {@code ADMIN_SUPER} đề xuất (Q8 ✅*) thì <i>mặc nhiên</i> có hai người.
 *       Kiểm hai lần: {@code AdminGuard} ở controller đọc vai trò trong phiên, service này
 *       đọc lại từ {@link UserAccountPort} nên vai trò vừa bị gỡ không còn duyệt được bằng
 *       một phiên cũ.</li>
 *   <li><b>Người đề xuất không tự duyệt.</b> Trường hợp duy nhất còn lại là một {@code DPO}
 *       vừa đề xuất (Q8 cho cả DPO) vừa bấm duyệt. Khi đó phải có DPO thứ hai ⇒
 *       {@code 409 DSAR_SELF_APPROVAL_FORBIDDEN}.</li>
 *   <li><b>Không chứng minh được ai đề xuất thì không duyệt.</b> Yêu cầu do admin nhập
 *       (kênh {@code EMAIL}/{@code POST}/{@code WEB_FORM}) mà {@code handled_by} rỗng là
 *       trạng thái không kiểm chứng được ⇒ từ chối, không "cho qua vì chắc là ổn".</li>
 * </ol>
 *
 * <p><b>Người đề xuất lưu ở đâu — và giới hạn đã biết.</b> {@code dsar_request} của p4 B5
 * <b>không có cột {@code proposed_by}/{@code created_by}</b>, và CLAUDE.md cấm thêm
 * migration ngoài danh mục p4 §4.9.2. Nên người đề xuất được chốt vào
 * {@code handled_by} ngay lúc L54 tạo yêu cầu, và {@code AdminDsarService} chặn
 * {@code ASSIGN} ghi đè cột đó trên yêu cầu {@code ERASE} do admin nhập. Hệ quả: với yêu
 * cầu {@code ERASE} nhập tay, {@code handled_by} mang nghĩa "người đề xuất", không phải
 * "người đang xử lý" — khác nghĩa cột ở các loại yêu cầu khác. Xem handoff H15.170.
 *
 * <p>Yêu cầu <b>tự phục vụ</b> ({@code channel = SELF_SERVICE}) không áp quy tắc này:
 * người đề xuất là chính chủ thể dữ liệu (p15 §15.4.6 luồng D+0), nên bất kỳ DPO nào cũng
 * là người thứ hai.</p>
 *
 * <p>Thực thi dùng {@link ErasureCoordinator} — cùng một đường mà
 * {@code ErasureExecutionJob} của p15 §15.4.6 sẽ dùng, nên không có hai định nghĩa "xoá
 * tài khoản nghĩa là xoá những bảng nào".</p>
 */
@Service
public class AdminErasureApprovalService {

    /** Vai trò duy nhất được duyệt xoá (p8 L53 {@code R:DPO}, p14 Q9). */
    public static final String ROLE_DPO = "DPO";

    private final DsarRequestPort dsarPort;
    private final ErasureCoordinator erasureCoordinator;
    private final UserAccountPort userAccountPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminErasureApprovalService(
            DsarRequestPort dsarPort,
            ErasureCoordinator erasureCoordinator,
            UserAccountPort userAccountPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.dsarPort = dsarPort;
        this.erasureCoordinator = erasureCoordinator;
        this.userAccountPort = userAccountPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * Duyệt + thực thi trong MỘT transaction: nếu một module xoá lỗi thì cả việc đánh dấu
     * {@code COMPLETED} cũng rollback — trạng thái "đã ghi hoàn tất mà dữ liệu còn đó" là
     * thứ duy nhất tệ hơn việc chưa xoá.
     *
     * @param approverId  DPO đang bấm duyệt
     * @param reason      lý do bắt buộc (ký hiệu {@code Rsn}) — đã validate ở controller
     */
    @Transactional
    public Result approve(UUID approverId, UUID requestId, String reason, RequestEvidence evidence) {
        UserAccountSnapshot approver = userAccountPort.snapshot(approverId);
        if (!approver.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        DsarRequest request = dsarPort.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if (request.requestType() != DsarRequestType.ERASE) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "requestType");
        }
        if (request.status() == DsarStatus.COMPLETED || request.status() == DsarStatus.REJECTED) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "terminalStatus");
        }
        if (request.identityVerifiedAt() == null) {
            // I30 + REQ-DSAR-04: xoá vĩnh viễn dữ liệu của một người mà chưa xác minh đó là
            // họ là cách biến quyền của chủ thể thành vũ khí của kẻ khác.
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED, request.publicRef());
        }
        requireSecondPerson(request, approverId);
        if (request.userId() == null) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "userId");
        }

        UUID subjectId = request.userId();
        erasureCoordinator.erase(subjectId);
        DsarRequest completed = request
                .withStatus(DsarStatus.COMPLETED)
                .withCompletedAt(clock.instant());
        dsarPort.update(completed);

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.dpo(approverId, ROLE_DPO))
                .subjectUser(subjectId)
                .action("DSAR_ERASURE_APPROVED")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("requestId", request.id())
                .meta("publicRef", request.publicRef())
                .meta("proposedBy", request.handledBy())
                .meta("channel", request.channel().name())
                .meta("erasureParticipants", erasureCoordinator.participantCount())
                .meta("reason", reason)
                .before(java.util.Map.of("status", request.status().name()))
                .after(java.util.Map.of("status", DsarStatus.COMPLETED.name()))
                .build());

        return new Result(request.publicRef(), subjectId, erasureCoordinator.participantCount(),
                completed.completedAt());
    }

    /**
     * Lớp 2 + 3 của quy tắc hai người. Tách riêng để test đọc được từng nhánh mà không phải
     * dựng cả đường xoá.
     */
    private static void requireSecondPerson(DsarRequest request, UUID approverId) {
        if (request.channel() == DsarChannel.SELF_SERVICE) {
            return;
        }
        UUID proposer = request.handledBy();
        if (proposer == null) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "proposerUnknown");
        }
        if (proposer.equals(approverId)) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_SELF_APPROVAL_FORBIDDEN, request.publicRef());
        }
    }

    /**
     * @param publicRef           mã tra cứu của yêu cầu vừa thực thi
     * @param subjectId           chủ thể dữ liệu đã bị xoá/ẩn danh hoá
     * @param participantsInvoked số module đã tham gia xoá (giám sát: thiếu module = sót dữ liệu)
     * @param completedAt         mốc hoàn tất ghi vào {@code dsar_request.completed_at}
     */
    public record Result(String publicRef, UUID subjectId, int participantsInvoked,
                         java.time.Instant completedAt) {
    }
}
