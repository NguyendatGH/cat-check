package com.catcheck.admin.application;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.admin.domain.AppSettingRow;
import com.catcheck.admin.domain.port.AppSettingAdminPort;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.shared.api.AdminETag;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * L69 {@code GET /admin/settings} va L70 {@code PATCH /admin/settings/{key}} (p8 §8.4.12 muc (f)).
 *
 * <p><b>Ba rang buoc cua L70, theo dung thu tu kiem, va vi sao thu tu do:</b></p>
 * <ol>
 *   <li><b>Khoa phai ton tai</b> ⇒ {@code 404 SETTING_KEY_UNKNOWN} (p8 §8.2.4(j)). Kiem truoc
 *       {@code If-Match} vi mot khoa khong ton tai thi khong co ETag nao de so — tra
 *       {@code 428} cho mot khoa khong ton tai la mot cau tra loi gay hieu nham.</li>
 *   <li><b>{@code If-Match} bat buoc</b> ⇒ thieu header {@code 428 PRECONDITION_REQUIRED},
 *       khong khop {@code 412 RESOURCE_MODIFIED} (p8 §8.1.11).</li>
 *   <li><b>Ghi {@code audit_log} trong CUNG transaction</b> — ky hieu {@code Aud} cua p8 §8.3.2:
 *       "audit fail ⇒ rollback ca hanh dong".</li>
 * </ol>
 *
 * <p><b>{@code before}/{@code after} cua khoa {@code secret} bi che.</b> p4 §H3 noi
 * {@code secret = true} nghia la che "trong UI <b>va trong audit</b>" — nen dung
 * {@link AppSettingRow#masked()} truoc khi dua vao {@link AuditEvent}. Audit van tra loi duoc cau
 * "ai doi khoa nao, luc nao, vi sao" (day la cau no phai tra loi); no khong tra loi "gia tri cu
 * la gi" — va voi mot khoa duoc danh dau nhay cam thi do la dung thiet ke, khong phai mat thong
 * tin.</p>
 */
@Service
public class AdminSettingsService {

    private final AppSettingAdminPort settings;
    private final AuditLogService auditLogService;

    public AdminSettingsService(AppSettingAdminPort settings, AuditLogService auditLogService) {
        this.settings = settings;
        this.auditLogService = auditLogService;
    }

    /** L69 — moi khoa, gia tri {@code secret} da che. */
    public List<AppSettingRow> listMasked() {
        return settings.findAll().stream().map(AppSettingRow::masked).toList();
    }

    /** ETag cua mot khoa, dinh dang {@code W/"<updatedAt millis>-<key>"} ({@code AdminETag}). */
    public static String etagOf(AppSettingRow row) {
        return AdminETag.of(row.updatedAt(), row.key());
    }

    /**
     * L70.
     *
     * @param ifMatch header {@code If-Match} nguyen ban; {@code null}/rong ⇒ {@code 428}
     * @param reason  da duoc {@code AdminGuard.requireReason} chuan hoa o controller
     * @return dong sau khi sua, gia tri {@code secret} da che
     */
    @Transactional
    public AppSettingRow update(String key, String newValue, String ifMatch, UUID adminId,
                                String adminRole, String reason, String requestId,
                                String ipAddress, String userAgent) {
        AppSettingRow current = settings.findByKey(key)
                .orElseThrow(() -> new NotFoundException(AdminOpsErrorCode.SETTING_KEY_UNKNOWN, key));
        AdminETag.requireMatch(ifMatch, etagOf(current));

        AppSettingRow updated = settings.updateValue(key, newValue, adminId)
                // Khoa vua doc duoc ma khong sua duoc nghia la co ai vua xoa no giua hai cau
                // lenh. Tra 404 thay vi 500: trang thai cuoi dung la "khoa khong ton tai".
                .orElseThrow(() -> new NotFoundException(AdminOpsErrorCode.SETTING_KEY_UNKNOWN, key));

        AppSettingRow maskedBefore = current.masked();
        AppSettingRow maskedAfter = updated.masked();
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("ADMIN_SETTING_UPDATED")
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("key", key)
                .meta("valueType", updated.valueType())
                .meta("secret", Boolean.TRUE.equals(updated.secret()))
                .meta("reason", reason)
                .before(java.util.Map.of("value", String.valueOf(maskedBefore.value())))
                .after(java.util.Map.of("value", String.valueOf(maskedAfter.value())))
                .build());
        return maskedAfter;
    }
}
