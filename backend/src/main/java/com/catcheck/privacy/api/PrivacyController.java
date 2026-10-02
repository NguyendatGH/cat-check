package com.catcheck.privacy.api;

import com.catcheck.privacy.api.dto.ConsentHistoryView;
import com.catcheck.privacy.api.dto.ConsentStateView;
import com.catcheck.privacy.api.dto.CreateDsarRequest;
import com.catcheck.privacy.api.dto.DataInventoryView;
import com.catcheck.privacy.api.dto.DsarRequestDetailView;
import com.catcheck.privacy.api.dto.DsarRequestView;
import com.catcheck.privacy.api.dto.ExportStatusView;
import com.catcheck.privacy.api.dto.PageView;
import com.catcheck.privacy.api.dto.PurposeView;
import com.catcheck.privacy.api.dto.RecordConsentsRequest;
import com.catcheck.privacy.application.ConsentCurrentState;
import com.catcheck.privacy.application.ConsentGrant;
import com.catcheck.privacy.application.ConsentService;
import com.catcheck.privacy.application.DsarService;
import com.catcheck.privacy.application.RequestEvidence;
import com.catcheck.privacy.domain.ConsentMethod;
import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.DataInventoryItem;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.PolicyVersion;
import com.catcheck.privacy.domain.port.DataInventoryItemPort;
import com.catcheck.privacy.domain.port.PolicyVersionPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Nhóm C của p8 §8.4.3 — Quyền riêng tư, consent & DSAR (15 endpoint user-facing C1–C15).
 *
 * <p>Tên path và {@code operationId} theo p8; client sinh từ OpenAPI và test hợp đồng
 * neo vào các tên này. Mọi endpoint user KHÔNG nhận {@code userId} từ client — chủ thể
 * lấy từ phiên đăng nhập (p11 §11.5.3 lớp 1, chống IDOR).</p>
 */
@RestController
@RequestMapping("/api/v1/privacy")
@Tag(name = "Privacy", description = "Consent, Trung tâm quyền riêng tư, DSAR, xoá tài khoản")
public class PrivacyController {

    private static final DateTimeFormatter CURSOR_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final ConsentService consentService;
    private final DsarService dsarService;
    private final DataInventoryItemPort dataInventoryItemPort;
    private final PolicyVersionPort policyVersionPort;
    private final Clock clock;

    public PrivacyController(
            ConsentService consentService,
            DsarService dsarService,
            DataInventoryItemPort dataInventoryItemPort,
            PolicyVersionPort policyVersionPort,
            Clock clock
    ) {
        this.consentService = consentService;
        this.dsarService = dsarService;
        this.dataInventoryItemPort = dataInventoryItemPort;
        this.policyVersionPort = policyVersionPort;
        this.clock = clock;
    }

    /**
     * C1 — danh mục {@code consent_purpose} đã resolve theo locale. Công khai: không
     * cần đăng nhập (p8 §8.4.3 dòng C1, Auth: —).
     */
    @Operation(
            operationId = "listConsentPurposes",
            summary = "Danh mục mục đích xử lý dữ liệu",
            description = """
                    Trả danh mục consent_purpose đã resolve theo locale (nhãn, mô tả, mandatory, \
                    sensitive). Công khai — không cần đăng nhập. Mỗi mục đích là MỘT checkbox \
                    riêng (p15 §15.3.1 C2); mục tuỳ chọn không bao giờ tick sẵn (I17).""")
    @GetMapping("/purposes")
    public ResponseEntity<PageView<PurposeView>> listPurposes(
            @RequestParam(name = "locale", required = false) String locale
    ) {
        String resolved = resolveLocale(locale, null);
        List<PurposeView> items = consentService.listPurposes(resolved).stream()
                .map(purpose -> new PurposeView(
                        purpose.code(),
                        pick(purpose.labelVy(), purpose.labelEn(), resolved),
                        pick(purpose.descriptionVy(), purpose.descriptionEn(), resolved),
                        purpose.isMandatory(),
                        purpose.isSensitiveData(),
                        purpose.defaultState(),
                        purpose.withdrawEffect(),
                        purpose.phase()))
                .toList();
        return ResponseEntity.ok(PageView.of(items, items.size(), null, false));
    }

    /** C2 — trạng thái hiện hành từng purpose của tôi. */
    @Operation(
            operationId = "getCurrentConsents",
            summary = "Trạng thái consent hiện hành của tôi",
            description = """
                    Trạng thái hiện hành của MỌI mục đích active. Mục chưa từng được hỏi trả \
                    status NONE — "im lặng không phải là đồng ý" (p15 §15.3.1 C4).""")
    @GetMapping("/consents")
    public ResponseEntity<PageView<ConsentStateView>> getCurrentConsents(@CurrentUser SecurityPrincipal user) {
        List<ConsentStateView> items = consentService.listCurrent(user.userId()).stream()
                .map(state -> new ConsentStateView(state.purposeCode(), state.status().name()))
                .toList();
        return ResponseEntity.ok(PageView.of(items, items.size(), null, false));
    }

    /** C3 — cấp/rút một hoặc nhiều purpose; ghi consent_record append-only. */
    @Operation(
            operationId = "recordConsents",
            summary = "Cấp hoặc rút consent theo mục đích",
            description = """
                    Cấp/rút nhiều purpose trong MỘT transaction. Mỗi lần ghi là INSERT dòng \
                    mới vào consent_record (append-only, I16) — rút = dòng WITHDRAWN + \
                    supersedes_id, không bao giờ UPDATE dòng cũ. Rút SERVICE_CORE bị từ chối \
                    409 CONSENT_MANDATORY_CANNOT_WITHDRAW. Ghi method, IP, UA, policy_hash, \
                    consent_text_hash (p15 §15.10-C).""")
    @PostMapping("/consents")
    public ResponseEntity<PageView<ConsentStateView>> recordConsents(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody RecordConsentsRequest request,
            @RequestParam(name = "uiSurface", required = false, defaultValue = "privacy_center") String uiSurface,
            @RequestParam(name = "locale", required = false) String locale,
            HttpServletRequest httpRequest
    ) {
        List<ConsentGrant> grants = request.consents().stream()
                .map(g -> new ConsentGrant(g.purposeCode(), g.granted()))
                .toList();
        List<ConsentCurrentState> states = consentService.recordConsents(
                user.userId(),
                grants,
                ConsentMethod.WEB_CHECKBOX,
                uiSurface,
                resolveLocale(locale, null),
                evidence(httpRequest));
        List<ConsentStateView> items = states.stream()
                .map(state -> new ConsentStateView(state.purposeCode(), state.status().name()))
                .toList();
        return ResponseEntity.ok(PageView.of(items, items.size(), null, false));
    }

    /** C4 — lịch sử consent của tôi — bằng chứng tuân thủ. */
    @Operation(
            operationId = "getConsentHistory",
            summary = "Lịch sử consent của tôi",
            description = """
                    Toàn bộ dòng consent_record của user, mới nhất trước — bằng chứng chứng minh \
                    đã lấy đồng ý hợp lệ (Điều 6.2 NĐ356). Chỉ trả của chính user đang đăng nhập.""")
    @GetMapping("/consents/history")
    public ResponseEntity<PageView<ConsentHistoryView>> getConsentHistory(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        List<ConsentRecord> records = consentService.history(user.userId(), limit);
        List<ConsentHistoryView> items = new ArrayList<>();
        for (ConsentRecord record : records) {
            String policyVersion = policyVersionPort.findById(record.policyVersionId())
                    .map(PolicyVersion::version)
                    .orElse(null);
            items.add(new ConsentHistoryView(
                    record.purposeCode(),
                    record.status().name(),
                    policyVersion,
                    record.policyHash(),
                    record.method().name(),
                    record.uiSurface(),
                    record.occurredAt()));
        }
        return ResponseEntity.ok(PageView.of(items, items.size(), null, false));
    }

    /** C5 — "Dữ liệu CatCheck đang giữ về bạn", render động từ data_inventory_item. */
    @Operation(
            operationId = "getDataInventory",
            summary = "Dữ liệu CatCheck đang giữ về bạn",
            description = """
                    Render ĐỘNG từ bảng data_inventory_item (p15 §15.4.3) — không phải văn bản \
                    chép tay. Gồm nhóm, mô tả, căn cứ pháp lý, thời hạn lưu, có chuyển ra \
                    nước ngoài không, bên nhận là ai.""")
    @GetMapping("/data-inventory")
    public ResponseEntity<PageView<DataInventoryView>> getDataInventory(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(name = "locale", required = false) String locale
    ) {
        String resolved = resolveLocale(locale, null);
        List<DataInventoryView> items = dataInventoryItemPort.findAllActive().stream()
                .map(item -> new DataInventoryView(
                        item.code(),
                        item.categoryVi(),
                        pick(item.descriptionVi(), item.descriptionEn(), resolved),
                        item.sensitivity().name(),
                        item.legalBasis().name(),
                        item.purposeCodes(),
                        item.retentionPolicyCode(),
                        item.storageLocation(),
                        item.crossBorder(),
                        item.recipient()))
                .toList();
        return ResponseEntity.ok(PageView.of(items, items.size(), null, false));
    }

    /** C6 — tạo dsar_request(ACCESS_EXPORT), 202. Giới hạn 1/24 giờ; bắt buộc step-up. */
    @Operation(
            operationId = "createDataExportRequest",
            summary = "Yêu cầu xuất dữ liệu cá nhân (DSAR)",
            description = """
                    Tạo dsar_request(ACCESS_EXPORT) trả 202. Giới hạn 1 yêu cầu/24 giờ \
                    (429 DSAR_EXPORT_RATE_LIMITED); bắt buộc step-up re-auth \
                    (403 DSAR_IDENTITY_VERIFICATION_REQUIRED). SLA phản hồi 02 ngày làm việc, \
                    thực hiện 10 ngày (p15 §15.4.1). Gói ZIP dựng bởi DataExportJob (p12) — \
                    ở M1 chưa có job nên download sẽ trả 409 DSAR_EXPORT_NOT_READY.""")
    @PostMapping("/export")
    public ResponseEntity<ExportStatusView> createExportRequest(
            @CurrentUser SecurityPrincipal user,
            HttpServletRequest httpRequest
    ) {
        DsarRequest request = dsarService.createExportRequest(user.userId(), evidence(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PrivacyDtoMapper.toExportStatus(request));
    }

    /** C7 — trạng thái gói xuất DSAR theo publicRef. */
    @Operation(
            operationId = "getExportStatus",
            summary = "Trạng thái gói xuất DSAR",
            description = """
                    Trạng thái dsar_request(ACCESS_EXPORT) theo mã publicRef. Không thuộc user \
                    ⇒ 404 (p8 §8.2.5).""")
    @GetMapping("/export/{publicRef}")
    public ResponseEntity<ExportStatusView> getExportStatus(
            @CurrentUser SecurityPrincipal user,
            @PathVariable("publicRef") String publicRef
    ) {
        return ResponseEntity.ok(PrivacyDtoMapper.toExportStatus(dsarService.getByPublicRef(user.userId(), publicRef)));
    }

    /**
     * C8 — tải gói ZIP: link MỘT LẦN, 72 giờ, yêu cầu đăng nhập. Ở M1 chưa có
     * DataExportJob nên gói chưa bao giờ sẵn sàng — luôn 409 DSAR_EXPORT_NOT_READY.
     */
    @Operation(
            operationId = "downloadExport",
            summary = "Tải gói xuất dữ liệu (một lần, 72 giờ)",
            description = """
                    Link tải một lần, hết hạn sau 72 giờ, yêu cầu đăng nhập (p15 §15.4.5). Ở M1 \
                    chưa có DataExportJob ⇒ gói chưa sẵn sàng, trả 409 DSAR_EXPORT_NOT_READY. \
                    Khi có job: chưa COMPLETED ⇒ 409; quá 72 giờ ⇒ 410 DSAR_EXPORT_EXPIRED; đã \
                    tải ⇒ 410 DSAR_EXPORT_ALREADY_DOWNLOADED.""")
    @GetMapping("/export/{publicRef}/download")
    public ResponseEntity<Void> downloadExport(
            @CurrentUser SecurityPrincipal user,
            @PathVariable("publicRef") String publicRef
    ) {
        dsarService.markDownloaded(user.userId(), publicRef);
        return ResponseEntity.noContent().build();
    }

    /** C9 — yêu cầu xoá tài khoản ⇒ DELETION_REQUESTED, ân hạn 7 ngày (TD-05). */
    @Operation(
            operationId = "requestAccountDeletion",
            summary = "Yêu cầu xoá tài khoản",
            description = """
                    Tạo dsar_request(ERASE) + chuyển tài khoản sang DELETION_REQUESTED với ân \
                    hạn 7 ngày (TD-05). Bắt buộc step-up. Đã có yêu cầu đang mở ⇒ 409 \
                    DELETION_ALREADY_REQUESTED.""")
    @PostMapping("/account-deletion")
    public ResponseEntity<ExportStatusView> requestAccountDeletion(
            @CurrentUser SecurityPrincipal user,
            HttpServletRequest httpRequest
    ) {
        DsarRequest request = dsarService.requestAccountDeletion(user.userId(), evidence(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PrivacyDtoMapper.toExportStatus(request));
    }

    /** C10 — huỷ yêu cầu xoá trong ân hạn (cũng gọi được từ link email). */
    @Operation(
            operationId = "cancelAccountDeletion",
            summary = "Huỷ yêu cầu xoá tài khoản",
            description = """
                    Huỷ dsar_request(ERASE) đang mở trong 7 ngày ân hạn. Hết hạn ⇒ 409 \
                    DELETION_GRACE_EXPIRED; không có yêu cầu ⇒ 409 DELETION_NOT_REQUESTED.""")
    @DeleteMapping("/account-deletion")
    public ResponseEntity<Void> cancelAccountDeletion(@CurrentUser SecurityPrincipal user) {
        dsarService.cancelAccountDeletion(user.userId());
        return ResponseEntity.noContent().build();
    }

    /** C11 — bật hạn chế xử lý ⇒ status = RESTRICTED (dữ liệu giữ nguyên, chỉ lưu trữ). */
    @Operation(
            operationId = "enableProcessingRestriction",
            summary = "Bật hạn chế xử lý dữ liệu",
            description = """
                    Chuyển tài khoản sang RESTRICTED (p15 §15.4.7): dữ liệu GIỮ NGUYÊN, hệ \
                    thống chỉ lưu trữ — không phân tích, không thông báo, không thống kê. Đang \
                    hạn chế ⇒ 409 RESTRICTION_ALREADY_ACTIVE.""")
    @PostMapping("/restriction")
    public ResponseEntity<Void> enableRestriction(
            @CurrentUser SecurityPrincipal user,
            HttpServletRequest httpRequest
    ) {
        dsarService.enableRestriction(user.userId(), evidence(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /** C12 — rút yêu cầu hạn chế ⇒ về ACTIVE. */
    @Operation(
            operationId = "disableProcessingRestriction",
            summary = "Rút yêu cầu hạn chế xử lý",
            description = "Chuyển tài khoản từ RESTRICTED về ACTIVE. Idempotent khi không hạn chế.")
    @DeleteMapping("/restriction")
    public ResponseEntity<Void> disableRestriction(@CurrentUser SecurityPrincipal user) {
        dsarService.disableRestriction(user.userId());
        return ResponseEntity.noContent().build();
    }

    /** C13 — DSAR không tự phục vụ được: RECTIFY, OBJECT, PROTECTION_MEASURE, COMPLAINT. */
    @Operation(
            operationId = "createDsarRequest",
            summary = "Tạo yêu cầu DSAR (RECTIFY/OBJECT/PROTECTION_MEASURE/COMPLAINT)",
            description = """
                    Tạo dsar_request cho các loại không tự phục vụ được. requestType ngoài \
                    danh sách ⇒ 400 DSAR_REQUEST_TYPE_UNSUPPORTED. SLA phản hồi 02 ngày làm \
                    việc, thực hiện 15 ngày (p15 §15.4.1). Rate limit 5/ngày do lớp Bucket4j \
                    của W3 ép (p8 §8.3.5).""")
    @PostMapping("/requests")
    public ResponseEntity<DsarRequestView> createDsarRequest(
            @CurrentUser SecurityPrincipal user,
            @Valid @RequestBody CreateDsarRequest request,
            HttpServletRequest httpRequest
    ) {
        DsarRequestType type;
        try {
            type = DsarRequestType.valueOf(request.requestType());
        } catch (IllegalArgumentException ex) {
            type = null;
        }
        // Bug that da sua: `DsarRequestType.valueOf` ngay tren da duoc boc try/catch, nhung
        // `DsarChannel.valueOf` thi khong — client gui `channel` sai (vd "EMAIL_X") lam
        // IllegalArgumentException thoat ra thanh 500 thay vi 400.
        DsarChannel channel;
        try {
            channel = request.channel() == null
                    ? DsarChannel.SELF_SERVICE
                    : DsarChannel.valueOf(request.channel());
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "channel");
        }
        DsarRequest created = dsarService.createManualRequest(user.userId(), type, channel, evidence(httpRequest));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(PrivacyDtoMapper.toRequestView(created));
    }

    /** C14 — danh sách yêu cầu của tôi + đếm ngược SLA (cursor pagination, p8 §8.1.4). */
    @Operation(
            operationId = "listDsarRequests",
            summary = "Danh sách yêu cầu DSAR của tôi",
            description = """
                    Các dsar_request của user, mới nhất trước, cursor pagination theo \
                    (received_at, id). Cursor không hợp lệ ⇒ 400 PAGINATION_CURSOR_INVALID.""")
    @GetMapping("/requests")
    public ResponseEntity<PageView<DsarRequestView>> listDsarRequests(
            @CurrentUser SecurityPrincipal user,
            @RequestParam(name = "limit", defaultValue = "20") int limit,
            @RequestParam(name = "cursor", required = false) String cursor
    ) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 100);
        Instant before = clock.instant();
        UUID beforeId = null;
        boolean hasMore = false;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = PrivacyDtoMapper.decodeCursor(cursor);
            try {
                before = Instant.from(CURSOR_INSTANT.parse(parts[0]));
                if (parts.length > 1 && !parts[1].isBlank()) {
                    beforeId = UUID.fromString(parts[1]);
                }
            } catch (DateTimeParseException | IllegalArgumentException ex) {
                throw new BusinessRuleException(PrivacyErrorCode.PAGINATION_CURSOR_INVALID);
            }
        }
        List<DsarRequest> requests = dsarService.listMine(user.userId(), before, beforeId, effectiveLimit + 1);
        if (requests.size() > effectiveLimit) {
            requests = requests.subList(0, effectiveLimit);
            hasMore = true;
        }
        List<DsarRequestView> items = new ArrayList<>();
        for (DsarRequest request : requests) {
            items.add(PrivacyDtoMapper.toRequestView(request));
        }
        String nextCursor = hasMore && !requests.isEmpty()
                ? PrivacyDtoMapper.encodeCursor(requests.get(requests.size() - 1))
                : null;
        return ResponseEntity.ok(PageView.of(items, effectiveLimit, nextCursor, hasMore));
    }

    /** C15 — chi tiết một yêu cầu; không thuộc user ⇒ 404 (p8 §8.2.5). */
    @Operation(
            operationId = "getDsarRequest",
            summary = "Chi tiết một yêu cầu DSAR",
            description = "Chi tiết dsar_request theo publicRef. Không thuộc user ⇒ 404 DSAR_NOT_FOUND.")
    @GetMapping("/requests/{publicRef}")
    public ResponseEntity<DsarRequestDetailView> getDsarRequest(
            @CurrentUser SecurityPrincipal user,
            @PathVariable("publicRef") String publicRef
    ) {
        return ResponseEntity.ok(PrivacyDtoMapper.toDetailView(dsarService.getByPublicRef(user.userId(), publicRef)));
    }

    
    
    
    
    
    private RequestEvidence evidence(HttpServletRequest httpRequest) {
        return RequestEvidence.of(
                httpRequest.getHeader("X-Request-Id"),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
    }

    private String resolveLocale(String locale, HttpServletRequest request) {
        if (locale != null && (locale.equals("vi") || locale.equals("en"))) {
            return locale;
        }
        if (request != null) {
            Locale browser = request.getLocale();
            if (browser != null && "en".equalsIgnoreCase(browser.getLanguage())) {
                return "en";
            }
        }
        return "vi";
    }

    private String pick(String vi, String en, String locale) {
        if ("en".equals(locale) && en != null) {
            return en;
        }
        return vi;
    }
}
