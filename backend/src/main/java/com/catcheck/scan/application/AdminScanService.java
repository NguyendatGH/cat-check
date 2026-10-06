package com.catcheck.scan.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.api.ScanReassignedEvent;
import com.catcheck.scan.application.spi.OpenDsarPort;
import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.ScanAssignment;
import com.catcheck.scan.domain.ScanReassignment;
import com.catcheck.scan.domain.ScanStatus;
import com.catcheck.scan.domain.port.CatOwnershipPort;
import com.catcheck.scan.domain.port.ScanQueryRepository;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import com.catcheck.scan.domain.port.ScanRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ba endpoint quản trị trên dữ liệu quét — <b>L5</b> ({@code GET /admin/users/{userId}/scans}),
 * <b>L6</b> ({@code GET /admin/scans/{scanId}/image}) và <b>L18</b>
 * ({@code POST /admin/scans/{scanId}/reassign-cat}), p8 §8.4.12 mục (a).
 *
 * <p><b>Mọi phương thức ở đây ghi {@code audit_log}, kể cả ĐỌC</b> — cột {@code Aud} của p8 bật
 * cho cả ba, và p15 REQ-AUD-04 cho phép chính người dùng xem lại ở {@code /account/privacy} ai
 * đã xem dữ liệu của mình. Đó là lý do một endpoint chỉ đọc vẫn {@code @Transactional} (không
 * {@code readOnly}): audit fail ⇒ rollback cả hành động (p8 §8.3.2 ký hiệu {@code Aud}).</p>
 *
 * <p><b>Phân quyền chia hai tầng, cố ý:</b> vai trò (cột {@code R:} của p8) kiểm ở tầng
 * {@code ..api..} bằng {@code AdminGuard} — nó chỉ cần đọc principal; còn điều kiện <i>dữ
 * liệu</i> ("có {@code dsar_request} đang mở không") kiểm ở đây vì nó cần DB. Gộp cả hai vào
 * controller sẽ buộc controller giữ một cổng persistence; gộp cả hai vào service sẽ buộc service
 * biết dạng tên vai trò của Spring Security.</p>
 */
@Service
public class AdminScanService {

    /** p8 §8.1.4: trang mặc định 20 dòng. */
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** p8 §8.1.4: trần cứng 100 dòng/trang, không phụ thuộc tham số client. */
    public static final int MAX_PAGE_SIZE = 100;

    /** {@code audit_log.action} của L6 — dùng ở cả ba nhánh (thành công, từ chối, lỗi). */
    private static final String ACTION_IMAGE_VIEW = "ADMIN_SCAN_IMAGE_VIEW";

    private static final Logger log = LoggerFactory.getLogger(AdminScanService.class);

    private final ScanRepository scanRepository;
    private final ScanQueryRepository scanQueryRepository;
    private final ScanReassignmentRepository reassignmentRepository;
    private final ScanImageAccessService imageAccessService;
    private final CatOwnershipPort catOwnershipPort;
    private final OpenDsarPort openDsarPort;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public AdminScanService(ScanRepository scanRepository,
                            ScanQueryRepository scanQueryRepository,
                            ScanReassignmentRepository reassignmentRepository,
                            ScanImageAccessService imageAccessService,
                            CatOwnershipPort catOwnershipPort,
                            OpenDsarPort openDsarPort,
                            AuditLogService auditLogService,
                            ApplicationEventPublisher eventPublisher,
                            UuidV7 uuidV7,
                            Clock clock) {
        this.scanRepository = scanRepository;
        this.scanQueryRepository = scanQueryRepository;
        this.reassignmentRepository = reassignmentRepository;
        this.imageAccessService = imageAccessService;
        this.catOwnershipPort = catOwnershipPort;
        this.openDsarPort = openDsarPort;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------ L5 */

    /**
     * Số liệu quét của một người dùng — {@code phValue}, phân loại, Lab (p8 L5).
     *
     * <p><b>Support và Super không bao giờ thấy ảnh.</b> Response <b>không có</b> URL ảnh ở bất
     * kỳ vai trò nào (đường duy nhất tới ảnh là L6), và ngay cả cờ <i>"có ảnh hay không"</i>
     * cũng chỉ trả cho {@code DPO} đang xử lý DSAR: với Support đó là thông tin vô dụng (họ
     * không mở được) nhưng lại là một nút mời gọi ở UI.</p>
     *
     * <p>Văn bản tự do (tên mèo, ghi chú tranh chấp) bị che cho Super/Support theo p11 §11.5.4;
     * {@code DPO} <b>đang có DSAR mở</b> thấy đầy đủ.</p>
     */
    @Transactional
    public AdminScanPage listUserScans(UUID userId, int page, int size, AdminActionContext context) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);
        boolean fullData = hasDsarFullAccess(userId, context);

        ScanQueryRepository.OffsetPage result =
                scanQueryRepository.findByUserForAdmin(userId, safePage * safeSize, safeSize);

        // Một dòng audit cho CẢ lần tra cứu, không một dòng mỗi scan: hành động là "đã mở số
        // liệu quét của người này", không phải "đã xem N bản ghi" (cùng lý do như L1).
        auditLogService.record(base(context, "ADMIN_USER_SCANS_VIEW", AuditSubjectType.SCAN, userId)
                .meta("page", safePage)
                .meta("size", safeSize)
                .meta("resultCount", result.items().size())
                .meta("unmaskedByDsar", fullData)
                .build());

        return new AdminScanPage(result.items(), result.totalElements(), safePage, safeSize, fullData);
    }

    /* ------------------------------------------------------------------ L6 */

    /**
     * Ảnh gốc của một lần quét — <b>chỉ {@code DPO}, và chỉ khi người dùng đó đang có một
     * {@code dsar_request} mở</b> (p8 L6, p14 ô Q5: <i>"vai trò duy nhất"</i>).
     *
     * <p>Vai trò đã được {@code AdminGuard} chặn ở controller; ở đây kiểm điều kiện còn lại.
     * Thứ tự kiểm là: scan tồn tại → có DSAR mở → mới mở ảnh. Nếu đảo lại (mở ảnh trước, kiểm
     * DSAR sau) thì một DPO không có DSAR vẫn làm hệ thống đọc file từ storage — và một lần đọc
     * đã xảy ra thì nhật ký truy cập của storage đã ghi, dù HTTP trả 403.</p>
     *
     * <p><b>Phương thức này cố ý KHÔNG {@code @Transactional} — bug thật đã đo được.</b> Khi nó
     * còn {@code @Transactional}, dòng {@code audit_log} {@code DENIED} ghi ngay trước
     * {@code throw} <b>bị cuốn theo rollback của chính ngoại lệ đó</b>: trên app thật, 403
     * {@code SCAN_IMAGE_DSAR_REQUIRED} để lại <b>0 dòng</b> trong {@code audit_log} (đếm được
     * đúng 1 dòng {@code SUCCESS} sau 2 lần gọi, một thành công một bị từ chối). Bỏ
     * {@code @Transactional} khiến mỗi lời gọi {@code AuditLogService.record} tự mở và commit
     * transaction riêng ({@code Propagation.REQUIRED}), nên bản ghi từ chối sống sót. Yêu cầu
     * "audit cùng transaction" của cột {@code Aud} không mất gì ở đây: L6 <b>không ghi dữ liệu
     * nghiệp vụ nào</b> để mà rollback, và điều cần giữ — "audit lỗi thì không phục vụ ảnh" —
     * được bảo đảm bằng thứ tự (ghi audit trước, mở stream sau). Xem handoff H15.157.</p>
     *
     * @throws NotFoundException {@code 404 SCAN_NOT_FOUND}
     * @throws PermissionDeniedException {@code 403 SCAN_IMAGE_DSAR_REQUIRED} khi không có yêu cầu
     *                                   DSAR nào đang mở
     */
    public ScanImageAccessService.Result openImageForDpo(UUID scanId, AdminActionContext context) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));

        if (!openDsarPort.hasOpenRequestFor(scan.getUserId())) {
            // Ghi cả lần BỊ TỪ CHỐI: p15 REQ-AUD-03 — nhật ký của một hành động nhạy cảm phải
            // trả lời được "đã có ai thử chưa", không chỉ "ai đã làm được".
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .outcome(AuditOutcome.DENIED)
                    .meta("scanId", scanId)
                    .meta("denyReason", "NO_OPEN_DSAR")
                    .build());
            throw new PermissionDeniedException(ScanErrorCode.SCAN_IMAGE_DSAR_REQUIRED);
        }

        try {
            ScanImageAccessService.Result result = imageAccessService.openForDsar(scan);
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .meta("scanId", scanId)
                    .build());
            log.info("DPO mở ảnh scan: scanId={} adminId={}", scanId, context.adminId());
            return result;
        } catch (NotFoundException | ConflictException ex) {
            // Ảnh không lưu / đã hết hạn vẫn là một lần ĐÃ THỬ MỞ ảnh của người khác: ghi lại,
            // rồi ném tiếp nguyên mã lỗi.
            auditLogService.record(base(context, ACTION_IMAGE_VIEW, AuditSubjectType.SCAN,
                    scan.getUserId())
                    .outcome(AuditOutcome.ERROR)
                    .meta("scanId", scanId)
                    .meta("denyReason", ex.errorCode().code())
                    .build());
            throw ex;
        }
    }

    /* ----------------------------------------------------------------- L18 */

    /**
     * Gán lại một lần quét cho mèo khác <b>sau</b> cửa sổ 24 giờ của người dùng (p8 L18,
     * p6 §6.10.3: <i>"Sau đó chỉ admin support sửa được"</i>).
     *
     * <p><b>Khác đường của người dùng</b> ({@code ScanLifecycleService#reassign}) ở ba điểm:</p>
     * <ol>
     *   <li><b>Không kiểm cửa sổ 24 giờ</b> — đó chính là lý do endpoint này tồn tại.</li>
     *   <li><b>Không kiểm trần 3 lần.</b> p6 §6.10.3 đặt trần với lý do "chống nghịch dữ liệu",
     *       tức là chống chính người dùng; áp nó lên đường sửa lỗi của tổng đài sẽ tạo ra những
     *       lần quét <i>không ai sửa được nữa</i>. Xem handoff H15.152.</li>
     *   <li>Mèo đích phải thuộc <b>chủ của lần quét</b>, không phải thuộc admin. Chuyển dữ liệu
     *       sức khoẻ sang tài khoản khác không phải "gán lại mèo" mà là rò rỉ dữ liệu.</li>
     * </ol>
     *
     * <p>{@code scan_reassignment.changed_by_role} ghi vai trò admin (snapshot), và
     * {@code reason} bắt buộc NOT NULL cho người đổi là admin — ép ở DB bằng
     * {@code CHECK (changed_by_role = 'USER' OR reason IS NOT NULL)} (p4 D13).</p>
     */
    @Transactional
    public AdminReassignResult reassign(UUID scanId, UUID toCatId, boolean toShared,
                                        AdminActionContext context) {
        Scan scan = scanRepository.findById(scanId)
                .orElseThrow(() -> new NotFoundException(ScanErrorCode.SCAN_NOT_FOUND));
        Instant now = clock.instant();

        if (scan.getDeletedAt() != null || scan.getStatus() != ScanStatus.ANALYZED) {
            throw new ConflictException(ScanErrorCode.SCAN_NOT_REASSIGNABLE);
        }

        UUID fromCatId = scan.getCatId();
        ScanAssignment fromAssignment = scan.getAssignment();
        ScanAssignment toAssignment;
        UUID targetCatId = toCatId;

        if (toShared) {
            toAssignment = ScanAssignment.SHARED_UNKNOWN;
            targetCatId = null;
        } else {
            if (targetCatId == null) {
                throw new BusinessRuleException(ScanErrorCode.SCAN_METADATA_INVALID);
            }
            if (targetCatId.equals(fromCatId)) {
                throw new ConflictException(ScanErrorCode.SCAN_REASSIGN_SAME_TARGET);
            }
            CatOwnershipPort.CatSnapshot target = catOwnershipPort.findSnapshot(targetCatId)
                    .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
            if (!target.isOwnedBy(scan.getUserId())) {
                // 404 CAT_NOT_FOUND, KHÔNG 403 CAT_NOT_OWNED: p8 §8.2.5 mục 1 bắt tài nguyên của
                // người khác trả 404 để không xác nhận nó tồn tại, và ngoại lệ 403 duy nhất mà mục
                // đó cho phép là `POST /scans` (ở đó `catId` do chính người dùng vừa chọn từ danh
                // sách của mình). Ở L18, `catId` là một id admin gõ vào — đúng kịch bản dò id mà
                // quy tắc nhắm tới. Đo thật: dùng `ScanErrorCode.CAT_NOT_OWNED` trả **403** vì
                // status gắn trên chính ErrorCode, không trên lớp exception.
                throw new NotFoundException(CatErrorCode.CAT_NOT_FOUND);
            }
            toAssignment = ScanAssignment.ASSIGNED;
        }

        scan.reassignTo(targetCatId, toAssignment, now);
        scanRepository.save(scan);

        reassignmentRepository.save(new ScanReassignment(
                uuidV7.generate(), scanId, fromCatId, targetCatId, fromAssignment, toAssignment,
                context.adminId(), context.adminRole(), context.reason(), now));

        // Cùng sự kiện với đường của người dùng: tính lại rule cho CẢ mèo cũ lẫn mèo mới và thu
        // hồi cảnh báo mồ côi (p6 §6.10.3) là hệ quả của việc đổi mèo, không phụ thuộc ai đổi.
        eventPublisher.publishEvent(new ScanReassignedEvent(scanId, fromCatId, targetCatId));

        auditLogService.record(base(context, "ADMIN_SCAN_REASSIGNED", AuditSubjectType.SCAN,
                scan.getUserId())
                .meta("scanId", scanId)
                // UUID dạng ĐỐI TƯỢNG, không phải chuỗi: `PiiRedactor` thay mọi chuỗi ≥ 32 ký tự
                // [A-Za-z0-9+/=_-] bằng `[REDACTED]_BLOB`, và một UUID có dấu gạch ngang khớp
                // đúng lớp ký tự đó ⇒ id bị xoá khỏi chính cột mà p4 §4.6.4 yêu cầu phải có id.
                // Nhánh UUID của redactor để nguyên giá trị. Xem handoff H15.156.
                .before(java.util.Map.of(
                        "catId", fromCatId == null ? "null" : fromCatId,
                        "assignment", fromAssignment.name()))
                .after(java.util.Map.of(
                        "catId", targetCatId == null ? "null" : targetCatId,
                        "assignment", toAssignment.name()))
                .build());

        return new AdminReassignResult(scanId, fromCatId, targetCatId,
                toAssignment.name(), scan.getReassignCount());
    }

    /* --------------------------------------------------------------- helper */

    /**
     * {@code DPO} <b>và</b> có {@code dsar_request} mở ⇒ xem đầy đủ; mọi trường hợp khác ⇒ dữ
     * liệu che. Thứ tự điều kiện có ý nghĩa: không phải DPO thì không truy vấn DSAR làm gì.
     */
    private boolean hasDsarFullAccess(UUID userId, AdminActionContext context) {
        return context.isDpo() && openDsarPort.hasOpenRequestFor(userId);
    }

    private AuditEvent.Builder base(AdminActionContext context, String action,
                                    AuditSubjectType subjectType, UUID subjectUserId) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(context.auditActor())
                .subject(subjectType, subjectUserId)
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent());
        if (context.reason() != null) {
            builder.meta("reason", context.reason());
        }
        return builder;
    }

    /**
     * Một trang số liệu quét cho màn quản trị.
     *
     * @param rows          các dòng của trang, mới nhất trước
     * @param totalElements tổng số lần quét chưa xoá của người dùng
     * @param page          số trang, đếm từ 0
     * @param size          kích thước trang đã kẹp về {@code [1, 100]}
     * @param fullData      {@code true} khi người gọi là DPO đang xử lý DSAR ⇒ KHÔNG che văn
     *                      bản tự do; {@code false} ⇒ tầng {@code api} phải che
     */
    public record AdminScanPage(
            List<ScanQueryRepository.Row> rows,
            long totalElements,
            int page,
            int size,
            boolean fullData
    ) {

        public AdminScanPage {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /**
     * Kết quả L18.
     *
     * @param scanId         lần quét vừa đổi
     * @param fromCatId      mèo trước đó, {@code null} nếu trước đó không gán cho bé nào
     * @param toCatId        mèo sau khi đổi, {@code null} khi đổi sang {@code SHARED_UNKNOWN}
     * @param toAssignment   giá trị {@code scan.assignment} sau khi đổi
     * @param reassignCount  tổng số lần lần quét này đã bị đổi mèo
     */
    public record AdminReassignResult(
            UUID scanId, UUID fromCatId, UUID toCatId, String toAssignment, short reassignCount) {
    }
}
