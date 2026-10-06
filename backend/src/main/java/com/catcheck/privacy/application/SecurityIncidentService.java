package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý hồ sơ sự cố lộ/mất dữ liệu (p4 B9, p15 REQ-INC-01 + Đ29.1.c NĐ356: giữ ≥ 5 năm).
 *
 * <p>Phục vụ L59/L60/L61 của p8 §8.4.12 (ô {@code Q27}: {@code DPO} đọc + ghi,
 * {@code ADMIN_SUPER} chỉ đọc) và đồng thời là service nội bộ để job/vận hành ghi hồ sơ.
 * {@code HIGH}/{@code CRITICAL} bắt buộc báo cơ quan quản lý trong 72 giờ kể từ
 * {@code detectedAt} (p15 §15.9.5); {@link #findOverdueAuthorityNotifications()} là nguồn
 * dữ liệu cho {@code IncidentDeadlineMonitorJob} (p12 §12.6.5).</p>
 *
 * <p><b>{@code retain_until} do service tính, không nhận từ client</b>: REQ-INC-01 chốt
 * {@code resolved_at + 5 năm}, và đó là thời hạn pháp lý (Đ29.1.c NĐ356) nên để người gọi
 * đặt tay là mở đường xoá hồ sơ sự cố sớm bằng một request.</p>
 */
@Service
public class SecurityIncidentService {

    /** Đ29.1.c NĐ356: hồ sơ sự cố giữ tối thiểu 5 năm kể từ ngày khắc phục xong. */
    public static final int RETAIN_YEARS_AFTER_RESOLVED = 5;

    /** Điều 23.1 Luật BVDLCN: hạn báo cơ quan chuyên trách (A05) kể từ khi phát hiện. */
    public static final long AUTHORITY_NOTIFICATION_HOURS = 72;

    private static final int MAX_SUMMARY_LENGTH = 4000;

    private final SecurityIncidentPort incidentPort;
    private final AuditLogService auditLogService;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public SecurityIncidentService(SecurityIncidentPort incidentPort, AuditLogService auditLogService,
                                   UuidV7 uuidV7, Clock clock) {
        this.incidentPort = incidentPort;
        this.auditLogService = auditLogService;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** Mở hồ sơ sự cố mới — {@code public_ref = INC-<năm>-<số>} sinh từ sequence của V6. */
    @Transactional
    public SecurityIncident openIncident(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes
    ) {
        return incidentPort.save(new SecurityIncident(
                uuidV7.generate(),
                incidentPort.nextPublicRef(),
                severity,
                category,
                summary,
                affectedSubjectCount,
                affectedDataCodes,
                clock.instant(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                clock.instant()));
    }

    /**
     * L60 — DPO mở hồ sơ sự cố qua API. {@code detectedAt} nhận từ người gọi vì sự cố thường
     * được phát hiện trước khi có người ngồi nhập hồ sơ, và chính mốc đó khởi động đồng hồ 72
     * giờ (p15 §15.9.5) — lấy {@code now()} sẽ làm hạn báo cáo trễ hơn thực tế. Mốc trong
     * tương lai bị từ chối.
     *
     * <p>{@code public_ref} truyền vào chỉ là chỗ giữ: {@code JdbcSecurityIncidentAdapter.save}
     * tự gọi {@code nextPublicRef()} trong cùng câu INSERT và trả về bản ghi mang mã thật.</p>
     */
    @Transactional
    public SecurityIncident open(UUID actorId, Command command, RequestEvidence evidence) {
        Instant now = clock.instant();
        Instant detectedAt = command.detectedAt() == null ? now : command.detectedAt();
        if (detectedAt.isAfter(now)) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "detectedAt");
        }
        String summary = requireSummary(command.summary());
        SecurityIncident saved = incidentPort.save(new SecurityIncident(
                uuidV7.generate(), "PENDING", command.severity(), command.category(), summary,
                command.affectedSubjectCount(), command.affectedDataCodes(), detectedAt,
                null, null, null, null, null, null, actorId, command.reportRef(), now));
        auditLogService.record(audit(actorId, saved, "SECURITY_INCIDENT_OPENED", command.reason(), evidence)
                .meta("severity", saved.severity().name())
                .meta("category", saved.category().name())
                .build());
        return saved;
    }

    /**
     * L61 — cập nhật phân loại, các mốc thông báo và kết luận. Merge-patch: field vắng mặt
     * nghĩa là không đổi (p8 §8.1.11), nên không có cách nào "xoá" một mốc đã ghi — một mốc
     * thông báo đã xảy ra thì không thể chưa xảy ra lại.
     */
    @Transactional
    public SecurityIncident patch(UUID actorId, UUID incidentId, Patch patch, RequestEvidence evidence) {
        SecurityIncident current = incidentPort.findById(incidentId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND));
        Instant now = clock.instant();

        Instant classifiedAt = mergeMilestone(current.classifiedAt(), patch.classifiedAt(), current, now, "classifiedAt");
        Instant containedAt = mergeMilestone(current.containedAt(), patch.containedAt(), current, now, "containedAt");
        Instant authorityNotifiedAt = mergeMilestone(current.authorityNotifiedAt(),
                patch.authorityNotifiedAt(), current, now, "authorityNotifiedAt");
        Instant subjectsNotifiedAt = mergeMilestone(current.subjectsNotifiedAt(),
                patch.subjectsNotifiedAt(), current, now, "subjectsNotifiedAt");
        Instant resolvedAt = mergeMilestone(current.resolvedAt(), patch.resolvedAt(), current, now, "resolvedAt");

        SecurityIncident updated = new SecurityIncident(
                current.id(),
                current.publicRef(),
                patch.severity() == null ? current.severity() : patch.severity(),
                patch.category() == null ? current.category() : patch.category(),
                patch.summary() == null ? current.summary() : requireSummary(patch.summary()),
                patch.affectedSubjectCount() == null
                        ? current.affectedSubjectCount() : patch.affectedSubjectCount(),
                patch.affectedDataCodes() == null
                        ? current.affectedDataCodes() : patch.affectedDataCodes(),
                current.detectedAt(),
                classifiedAt,
                containedAt,
                authorityNotifiedAt,
                subjectsNotifiedAt,
                resolvedAt,
                // REQ-INC-01: retain_until = resolved_at + 5 năm, tính lại mỗi lần resolved_at đổi.
                resolvedAt == null ? current.retainUntil()
                        : resolvedAt.plus(RETAIN_YEARS_AFTER_RESOLVED * 365L, ChronoUnit.DAYS),
                actorId,
                patch.reportRef() == null ? current.reportRef() : patch.reportRef(),
                current.createdAt());
        incidentPort.update(updated);

        auditLogService.record(audit(actorId, updated, "SECURITY_INCIDENT_UPDATED", patch.reason(), evidence)
                .before(milestones(current))
                .after(milestones(updated))
                .build());
        return updated;
    }

    /** L59 — hàng chờ hồ sơ sự cố. */
    public List<SecurityIncident> list(IncidentSeverity severity, Boolean unresolved, int offset, int limit) {
        return incidentPort.findForAdmin(severity, unresolved, offset, limit);
    }

    public long count(IncidentSeverity severity, Boolean unresolved) {
        return incidentPort.countForAdmin(severity, unresolved);
    }

    /** L62 — {@code incidentId} bắt buộc phải trỏ tới một hồ sơ thật, nếu không ⇒ 404. */
    public SecurityIncident get(UUID incidentId) {
        return incidentPort.findById(incidentId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND));
    }

    /** Cập nhật các mốc điều trị (classified/contained/authority/subjects/resolved/retain_until). */
    @Transactional
    public void updateIncident(SecurityIncident incident) {
        incidentPort.update(incident);
    }

    /** HIGH/CRITICAL chưa báo cơ quan quản lý quá 72 giờ — nguồn cho job giám sát (p4 B9). */
    public List<SecurityIncident> findOverdueAuthorityNotifications() {
        return incidentPort.findOverdueAuthorityNotification(clock.instant());
    }

    /** Hạn 72 giờ báo A05 của một hồ sơ (p15 §15.9.5) — để UI đếm ngược. */
    public static Instant authorityDeadline(SecurityIncident incident) {
        return incident.detectedAt().plusSeconds(AUTHORITY_NOTIFICATION_HOURS * 3600);
    }

    private static Instant mergeMilestone(Instant current, Instant requested, SecurityIncident incident,
                                          Instant now, String field) {
        if (requested == null) {
            return current;
        }
        if (requested.isAfter(now) || requested.isBefore(incident.detectedAt())) {
            // Một mốc nằm trước lúc phát hiện hoặc trong tương lai làm sai mọi phép đo SLA
            // dựa trên bảng này (kể cả hạn 72 giờ của REQ-INC-02).
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, field);
        }
        return requested;
    }

    private static String requireSummary(String summary) {
        String trimmed = summary == null ? "" : summary.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_SUMMARY_LENGTH) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "summary");
        }
        return trimmed;
    }

    private static AuditEvent.Builder audit(UUID actorId, SecurityIncident incident, String action,
                                            String reason, RequestEvidence evidence) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(AuditActor.dpo(actorId, AdminErasureApprovalService.ROLE_DPO))
                // Chủ thể là hệ thống, KHÔNG phải một user: hồ sơ sự cố thường liên quan nhiều
                // chủ thể dữ liệu và `subject_user_id` chỉ chứa được một id.
                .subject(AuditSubjectType.SYSTEM, null)
                .action(action)
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("incidentId", incident.id())
                .meta("publicRef", incident.publicRef());
        if (reason != null && !reason.isBlank()) {
            builder.meta("reason", reason.strip());
        }
        return builder;
    }

    private static java.util.Map<String, Object> milestones(SecurityIncident incident) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("severity", incident.severity().name());
        values.put("category", incident.category().name());
        values.put("classifiedAt", String.valueOf(incident.classifiedAt()));
        values.put("containedAt", String.valueOf(incident.containedAt()));
        values.put("authorityNotifiedAt", String.valueOf(incident.authorityNotifiedAt()));
        values.put("subjectsNotifiedAt", String.valueOf(incident.subjectsNotifiedAt()));
        values.put("resolvedAt", String.valueOf(incident.resolvedAt()));
        values.put("retainUntil", String.valueOf(incident.retainUntil()));
        return values;
    }

    /**
     * Lệnh mở hồ sơ (L60).
     *
     * @param reason lý do bắt buộc (ký hiệu {@code Rsn} của p8), đã validate ở controller
     */
    public record Command(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes,
            Instant detectedAt,
            String reportRef,
            String reason) {
    }

    /** Merge-patch cho L61: field {@code null} = không đổi. */
    public record Patch(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes,
            Instant classifiedAt,
            Instant containedAt,
            Instant authorityNotifiedAt,
            Instant subjectsNotifiedAt,
            Instant resolvedAt,
            String reportRef,
            String reason) {
    }
}
