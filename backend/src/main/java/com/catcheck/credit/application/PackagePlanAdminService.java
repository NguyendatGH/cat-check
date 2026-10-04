package com.catcheck.credit.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.credit.api.CreditErrorCode;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;
import com.catcheck.credit.domain.port.PackagePlanPort;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cấu hình gói — L25, L26 (p8 §8.4.12 mục (b); màn ở p14 §14.3.2 mục 5).
 *
 * <p><b>Vai trò ghi: chỉ {@code ADMIN_SUPER}.</b> p8 L26 ghi
 * {@code R:ADMIN_SUPER,ADMIN_CATALOG}, nhưng p11 §11.5.4 — <i>"bảng ma trận quyền DUY NHẤT của
 * hệ thống"</i>, và p11 sở hữu miền phân quyền theo TD-0x — đã cắt {@code ADMIN_CATALOG} khỏi
 * quyền sửa {@code package_plan} với lý do nêu rõ: đổi {@code credit_amount} là đổi quyền lợi đã
 * trả tiền của mọi người dùng. Theo thứ tự thẩm quyền, controller kiểm theo p11. Xem handoff
 * H15.100.</p>
 *
 * <p>Ghi {@code before}/{@code after} vào {@code audit_log} (p14 §14.3.2 mục 5 action
 * {@code PACKAGE_PLAN_UPDATE}) — đây là dữ liệu có hệ quả tài chính trực tiếp, nên bản ghi phải
 * đủ để dựng lại giá trị cũ mà không cần một bảng lịch sử riêng (bảng đó không có trong p4).</p>
 */
@Service
public class PackagePlanAdminService {

    private static final Logger log = LoggerFactory.getLogger(PackagePlanAdminService.class);

    private final PackagePlanPort packagePlanPort;
    private final AuditLogService auditLogService;

    public PackagePlanAdminService(PackagePlanPort packagePlanPort, AuditLogService auditLogService) {
        this.packagePlanPort = packagePlanPort;
        this.auditLogService = auditLogService;
    }

    /* ------------------------------------------------------------------ L25 */

    @Transactional(readOnly = true)
    public List<PackagePlanAdminView> list(boolean includeInactive) {
        return packagePlanPort.findAllForAdmin(includeInactive);
    }

    @Transactional(readOnly = true)
    public PackagePlanAdminView require(String code) {
        return packagePlanPort.findForAdmin(code)
                .orElseThrow(() -> new NotFoundException(CreditErrorCode.PACKAGE_PLAN_NOT_FOUND, code));
    }

    /* ------------------------------------------------------------------ L26 */

    /**
     * Ghi một lần sửa. Người gọi ({@code ..api..}) đã kiểm {@code If-Match} trên giá trị đọc
     * được từ {@link #require} nên ở đây không kiểm lại — nhưng vẫn đọc lại trong cùng
     * transaction để {@code before} là ảnh chụp thật, không phải ảnh của một request trước.
     *
     * @return gói sau khi sửa, kèm {@code updated_at} mới cho ETag kế tiếp
     */
    @Transactional
    public PackagePlanAdminView update(String code, PackagePlanUpdate update, AdminActionContext context) {
        PackagePlanAdminView before = require(code);
        if (!update.isEmpty()) {
            packagePlanPort.update(code, update);
        }
        PackagePlanAdminView after = require(code);

        auditLogService.record(AuditEvent.builder()
                .actor(context.auditActor())
                .subject(AuditSubjectType.SETTING, null)
                .action("PACKAGE_PLAN_UPDATE")
                .outcome(AuditOutcome.SUCCESS)
                .meta("packageCode", code)
                .meta("reason", context.reason())
                .before(snapshot(before.plan()))
                .after(snapshot(after.plan()))
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        log.info("Admin sửa cấu hình gói {}: version {} -> {}",
                code, before.plan().version(), after.plan().version());
        return after;
    }

    /**
     * Ảnh chụp cho {@code before}/{@code after}. Chỉ các trường L26 sửa được + {@code version}:
     * nhồi cả dòng vào đây làm diff khó đọc mà không thêm thông tin nào.
     */
    private static Map<String, Object> snapshot(PackagePlan plan) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("creditAmount", plan.creditAmount());
        values.put("creditValidityDays", plan.creditValidityDays());
        values.put("maxCatProfiles", plan.maxCatProfiles());
        values.put("active", plan.active());
        values.put("version", plan.version());
        values.put("historyLevel", plan.features().history().name());
        values.put("trend", plan.features().trend());
        values.put("reminder", plan.features().reminder());
        values.put("export", plan.features().export());
        values.put("storeImage", plan.features().storeImage());
        return values;
    }
}
