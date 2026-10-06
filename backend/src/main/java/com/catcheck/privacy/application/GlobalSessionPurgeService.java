package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.application.export.DsarExportJobPort;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * L62 — {@code POST /admin/security/sessions/purge-all}: bước <b>R1</b> của sổ tay sự cố
 * (p11 §11.13.4), <i>"bắt buộc trong mọi sự cố rò DB"</i>, mục tiêu hoàn tất ≤ 5 phút.
 *
 * <p><b>{@code incidentId} là tham số bắt buộc, không phải metadata trang trí</b> (p8 §8.4.12
 * ô L62: {@code Rsn} kèm {@code incidentId}). Hành động này đăng xuất <b>mọi</b> người dùng
 * và buộc mọi admin nhập lại TOTP; nó chỉ hợp lệ bên trong một sự cố đã được mở hồ sơ, và
 * §11.13.4 đòi audit {@code SECURITY.SESSIONS_PURGED_ALL} kèm {@code revokedCount},
 * {@code reason}, {@code incidentId}. Vì vậy service kiểm hồ sơ sự cố <b>tồn tại thật</b>
 * trước khi xoá gì — không có đường "purge trước, điền số hồ sơ sau".</p>
 *
 * <p>Thứ tự có chủ đích: hết hạn link tải gói DSAR <b>trước</b>, rồi mới xoá phiên. Hai
 * việc nằm trong cùng transaction nên thứ tự không đổi kết quả cuối, nhưng nếu transaction
 * vỡ ở giữa thì trạng thái còn lại là "link đã chết, phiên còn sống" — nhẹ hơn chiều ngược
 * lại ("phiên đã chết nhưng gói dữ liệu cá nhân vẫn tải được bằng link cũ").</p>
 */
@Service
public class GlobalSessionPurgeService {

    /** Mục tiêu vận hành của p8 L62 / p11 §11.13.4 — ghi vào audit để hậu kiểm đo được. */
    public static final Duration TARGET_DURATION = Duration.ofMinutes(5);

    private final GlobalSessionPurgePort sessionPurgePort;
    private final SecurityIncidentService incidentService;
    private final DsarExportJobPort dsarExportJobPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public GlobalSessionPurgeService(
            GlobalSessionPurgePort sessionPurgePort,
            SecurityIncidentService incidentService,
            DsarExportJobPort dsarExportJobPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.sessionPurgePort = sessionPurgePort;
        this.incidentService = incidentService;
        this.dsarExportJobPort = dsarExportJobPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * @param actorId    {@code ADMIN_SUPER} đang bấm (p8 L62 {@code R:ADMIN_SUPER})
     * @param actorRole  vai trò dùng cho {@code audit_log.actor_role}
     * @param incidentId hồ sơ {@code security_incident} đang mở; không tồn tại ⇒ 404
     * @param reason     lý do bắt buộc, đã validate ở controller
     */
    @Transactional
    public Result purgeAll(UUID actorId, String actorRole, UUID incidentId, String reason,
                           RequestEvidence evidence) {
        SecurityIncident incident = incidentService.get(incidentId);
        Instant startedAt = clock.instant();

        int downloadLinksRevoked = dsarExportJobPort.revokeAllDownloadLinks(startedAt);
        GlobalSessionPurgePort.PurgeOutcome outcome = sessionPurgePort.purgeEverySession();

        Instant finishedAt = clock.instant();
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(actorId, actorRole))
                // Chủ thể là toàn hệ thống: hành động không nhắm vào một user nào.
                .subject(AuditSubjectType.SYSTEM, null)
                .action("SECURITY.SESSIONS_PURGED_ALL")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("incidentId", incident.id())
                .meta("incidentRef", incident.publicRef())
                .meta("reason", reason)
                .meta("revokedCount", outcome.httpSessionsDeleted())
                .meta("deviceSessionsRevoked", outcome.deviceSessionsRevoked())
                .meta("otpInvalidated", outcome.otpInvalidated())
                .meta("dsarDownloadLinksRevoked", downloadLinksRevoked)
                .meta("elapsedMillis", Duration.between(startedAt, finishedAt).toMillis())
                .build());

        return new Result(incident.publicRef(), outcome.httpSessionsDeleted(),
                outcome.deviceSessionsRevoked(), outcome.otpInvalidated(), downloadLinksRevoked,
                Duration.between(startedAt, finishedAt).toMillis());
    }

    /**
     * @param incidentRef           mã hồ sơ sự cố đã ghi vào audit
     * @param revokedCount          số phiên HTTP thật đã xoá — con số §11.13.4 đòi ghi audit
     * @param deviceSessionsRevoked số dòng bản sao hiển thị đã đánh dấu thu hồi
     * @param otpInvalidated        số OTP/ticket đang mở đã vô hiệu
     * @param dsarDownloadLinksRevoked số gói dữ liệu cá nhân đã hết hạn link tải
     * @param elapsedMillis         thời gian thực thi — đối chiếu mục tiêu ≤ 5 phút
     */
    public record Result(String incidentRef, int revokedCount, int deviceSessionsRevoked,
                         int otpInvalidated, int dsarDownloadLinksRevoked, long elapsedMillis) {
    }
}
