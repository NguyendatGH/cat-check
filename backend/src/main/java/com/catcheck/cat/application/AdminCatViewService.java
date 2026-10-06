package com.catcheck.cat.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.cat.application.spi.OpenDsarPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.port.CatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ mèo của một người dùng, xem từ màn quản trị — <b>L4</b>
 * ({@code GET /admin/users/{userId}/cats}, p8 §8.4.12 mục (a); màn ở p14 §14.3.2).
 *
 * <p><b>Ghi {@code audit_log} dù chỉ đọc</b> — cột {@code Aud} của p8 bật cho L4, và p15
 * REQ-AUD-04 cho phép chính người dùng xem lại ở {@code /account/privacy} ai đã xem hồ sơ của
 * mình. Đó là lý do một endpoint chỉ đọc vẫn {@code @Transactional} (không {@code readOnly}):
 * audit fail ⇒ rollback cả hành động (p8 §8.3.2 ký hiệu {@code Aud}).</p>
 *
 * <p><b>Phân quyền chia hai tầng, cố ý:</b> vai trò (cột {@code R:} của p8) kiểm ở tầng
 * {@code ..api..} bằng {@code AdminGuard}; còn điều kiện <i>dữ liệu</i> ("có
 * {@code dsar_request} đang mở không") kiểm ở đây vì nó cần DB.</p>
 */
@Service
public class AdminCatViewService {

    private final CatRepository catRepository;
    private final OpenDsarPort openDsarPort;
    private final AuditLogService auditLogService;

    public AdminCatViewService(CatRepository catRepository,
                               OpenDsarPort openDsarPort,
                               AuditLogService auditLogService) {
        this.catRepository = catRepository;
        this.openDsarPort = openDsarPort;
        this.auditLogService = auditLogService;
    }

    /**
     * Toàn bộ hồ sơ mèo của một người dùng, <b>kể cả bé đã gỡ</b>
     * ({@code status = ARCHIVED}).
     *
     * <p>Lấy cả bé đã gỡ là có chủ ý: tổng đài nhận cuộc gọi về một lần quét cũ thì bé liên quan
     * rất có thể đã được gỡ hồ sơ — ẩn nó đi là ẩn đúng dữ liệu đang được hỏi. Hồ sơ <b>đã xoá
     * mềm</b> ({@code deleted_at}) thì không: bản ghi đã xoá thuộc phạm vi DSAR, không phải phạm
     * vi tra cứu hỗ trợ (và {@code findAllByOwnerId} đã lọc sẵn).</p>
     *
     * <p><b>Bẫy thật đã sa vào rồi gỡ:</b> truyền {@code statusFilter = null} với ý "mọi trạng
     * thái" trả về <b>chỉ các bé ARCHIVED</b>. JPQL của {@code CatJpaRepository} là
     * {@code (c.status = :statusFilter OR (:includeArchived = true AND c.status = ARCHIVED))} —
     * {@code c.status = null} không bao giờ đúng trong SQL ba giá trị, nên nhánh đầu tắt hẳn và
     * chỉ còn nhánh ARCHIVED. Đo thật trên tài khoản demo (3 bé ACTIVE): L4 trả
     * {@code items: []}. Vì vậy ở đây truyền {@code ACTIVE} <b>cộng</b>
     * {@code includeArchived = true} để lấy đúng hợp của hai trạng thái. Xem handoff H15.159.</p>
     *
     * <p>KHÔNG phân trang: {@code cat.max_per_user} là trần cứng 8 hồ sơ/tài khoản (C31), nên
     * phân trang một danh sách tối đa 8 phần tử là chi phí thừa — đúng cột {@code Trang = —} của
     * p8 L4 và quy ước "không phân trang" của §8.1.4.</p>
     *
     * @param userId  chủ hồ sơ
     * @param context ai đang xem, vì lý do gì
     * @return danh sách hồ sơ + cờ nói rõ có đang che văn bản tự do hay không
     */
    @Transactional
    public AdminCatListing listCatsOf(UUID userId, AdminActionContext context) {
        boolean fullData = hasDsarFullAccess(userId, context);
        List<Cat> cats = catRepository.findAllByOwnerId(userId, CatStatus.ACTIVE, true);

        // Một dòng audit cho CẢ lần xem, không một dòng mỗi bé: hành động là "đã mở hồ sơ mèo
        // của người này" (cùng lý do như L1/L5).
        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.CAT, userId)
                .action("ADMIN_USER_CATS_VIEW")
                .outcome(AuditOutcome.SUCCESS)
                .meta("reason", context.reason())
                .meta("catCount", cats.size())
                .meta("unmaskedByDsar", fullData)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new AdminCatListing(cats, fullData);
    }

    /**
     * {@code DPO} <b>và</b> có {@code dsar_request} mở ⇒ xem đầy đủ; mọi trường hợp khác ⇒ dữ
     * liệu che. Thứ tự điều kiện có ý nghĩa: không phải DPO thì không truy vấn DSAR làm gì.
     */
    private boolean hasDsarFullAccess(UUID userId, AdminActionContext context) {
        return context.isDpo() && openDsarPort.hasOpenRequestFor(userId);
    }

    /**
     * @param cats     hồ sơ mèo đã đọc
     * @param fullData {@code true} khi người gọi là DPO đang xử lý DSAR ⇒ KHÔNG che văn bản tự
     *                 do; {@code false} ⇒ tầng {@code api} phải che
     */
    public record AdminCatListing(List<Cat> cats, boolean fullData) {

        public AdminCatListing {
            cats = cats == null ? List.of() : List.copyOf(cats);
        }
    }
}
