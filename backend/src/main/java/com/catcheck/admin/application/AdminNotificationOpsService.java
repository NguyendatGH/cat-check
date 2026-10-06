package com.catcheck.admin.application;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.notification.api.OutboxAdminGateway;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * L67 {@code POST /admin/notifications/outbox/{id}/resend} va L72
 * {@code POST /admin/system/broadcast} (p8 §8.4.12 muc (f), p14 §14.2.2 o Q26/Q30).
 *
 * <p>Lop nay <b>khong</b> chua nghiep vu gui: no uy quyen cho hai cong cua module
 * {@code notification} ({@link OutboxAdminGateway}, {@link SystemBroadcastGateway}) va chi lam
 * ba viec cua tang admin — dich ket qua sang ma HTTP, ghi {@code audit_log}, va giu ca hai trong
 * cung mot transaction theo ky hieu {@code Aud} cua p8 §8.3.2.</p>
 */
@Service
public class AdminNotificationOpsService {

    private final OutboxAdminGateway outboxAdmin;
    private final SystemBroadcastGateway broadcast;
    private final AuditLogService auditLogService;

    public AdminNotificationOpsService(OutboxAdminGateway outboxAdmin,
                                       SystemBroadcastGateway broadcast,
                                       AuditLogService auditLogService) {
        this.outboxAdmin = outboxAdmin;
        this.broadcast = broadcast;
        this.auditLogService = auditLogService;
    }

    /**
     * L67. <b>Khong step-up, khong {@code reason}</b> — p8 ghi ro ly do: "chi day lai noi dung da
     * soan", khong co du lieu moi va khong co quyet dinh nghiep vu nao. Van ghi
     * {@code audit_log} ({@code Aud}) vi day la thao tac admin len du lieu nguoi dung.
     *
     * @throws NotFoundException {@code 404 OUTBOX_ENTRY_NOT_FOUND}
     * @throws ConflictException {@code 409 OUTBOX_ENTRY_NOT_FAILED}
     */
    @Transactional
    public void resendOutboxEntry(UUID outboxId, UUID adminId, String adminRole,
                                  String requestId, String ipAddress, String userAgent) {
        OutboxAdminGateway.ResendOutcome outcome = outboxAdmin.resendFailed(outboxId);
        switch (outcome) {
            case NOT_FOUND -> throw new NotFoundException(
                    AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FOUND, outboxId);
            case NOT_FAILED -> throw new ConflictException(
                    AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FAILED, outboxId);
            case REQUEUED -> auditLogService.record(AuditEvent.builder()
                    .actor(AuditActor.admin(adminId, adminRole))
                    .subject(AuditSubjectType.SYSTEM, null)
                    .action("ADMIN_OUTBOX_RESEND")
                    // outboxId KHONG phai PII (no la id mot ban ghi giao van), con dia chi nhan
                    // thi co — nen o day chi ghi id, khong ghi `to_address`. p16 §16.6.3.
                    //
                    // Truyen UUID chu KHONG phai `outboxId.toString()` — bug that, do duoc tren
                    // DB local: `PiiRedactor` cua module audit bo qua gia tri kieu UUID nhung
                    // chay `LONG_TOKEN_LIKE` ({@code [A-Za-z0-9+/=_-]{32,}}) tren moi String, nen
                    // mot UUID da .toString() bi ghi thanh `"[REDACTED]_BLOB"`. Hau qua: dong
                    // audit cua L67 mat DUY NHAT du lieu dinh danh cua no — khong con tra loi
                    // duoc "da gui lai ban ghi nao", dung cau ma ky hieu `Aud` cua p8 §8.3.2 ton
                    // tai de tra loi.
                    .meta("outboxId", outboxId)
                    .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                    .build());
        }
    }

    /**
     * L72. Kiem {@code templateCode} <b>o day</b> truoc khi goi cong, de tra
     * {@code 422 BROADCAST_TEMPLATE_NOT_ALLOWED} thay vi {@code 400} chung cua registry
     * template — mot template ngoai danh sach la vi pham rang buoc nghiep vu, khong phai cu phap
     * sai (p8 §8.1.12 phan biet {@code 400} va {@code 422}).
     *
     * <p><b>{@code dryRun} khong phai tien nghi.</b> Day la endpoint duy nhat co the ghi mot ban
     * ghi cho MOI user chi bang mot request; tren mot DB dung chung, bam that de "xem co chay
     * khong" la mot hanh dong khong hoan tac duoc. Cung tinh than voi L58
     * {@code .../dry-run} cua p8.</p>
     */
    @Transactional
    public SystemBroadcastGateway.BroadcastResult broadcast(
            String templateCode, String title, String body, Map<String, Object> payload,
            boolean dryRun, UUID adminId, String adminRole, String reason,
            String requestId, String ipAddress, String userAgent) {
        if (!SystemBroadcastGateway.ALLOWED_TEMPLATE_CODES.contains(templateCode)) {
            throw new BusinessRuleException(
                    AdminOpsErrorCode.BROADCAST_TEMPLATE_NOT_ALLOWED,
                    String.join(" | ", SystemBroadcastGateway.ALLOWED_TEMPLATE_CODES
                            .stream().sorted().toList()));
        }
        SystemBroadcastGateway.BroadcastResult result =
                broadcast.broadcast(templateCode, title, body, payload, dryRun);
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("ADMIN_SYSTEM_BROADCAST")
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("templateCode", templateCode)
                .meta("dryRun", dryRun)
                .meta("recipientsMatched", result.recipientsMatched())
                .meta("notificationsQueued", result.notificationsQueued())
                .meta("dedupeKeyPrefix", result.dedupeKeyPrefix())
                .meta("reason", reason)
                .build());
        return result;
    }
}
