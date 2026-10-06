package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng quản lý {@code security_incident} (p4 B9). API nằm ở nhóm admin L (M6) — ở M1
 * chỉ có service nội bộ để job/vận hành ghi hồ sơ sự cố.
 */
public interface SecurityIncidentPort {

    /** {@code public_ref} sinh từ sequence {@code security_incident_public_ref_seq}. */
    SecurityIncident save(SecurityIncident incident);

    /** Sinh mã {@code INC-2026-0007} tiếp theo. */
    String nextPublicRef();

    void update(SecurityIncident incident);

    /**
     * Hồ sơ HIGH/CRITICAL chưa báo cơ quan quản lý quá 72 giờ kể từ {@code detectedAt}
     * (p4 B9) — nguồn dữ liệu cho {@code IncidentDeadlineMonitorJob} (p12 §12.6.5).
     */
    List<SecurityIncident> findOverdueAuthorityNotification(Instant now);

    Optional<SecurityIncident> findById(UUID id);

    /**
     * L59 — hàng chờ sự cố, mới phát hiện trước, phân trang offset (p8 §8.4.12 cột Trang = {@code O}).
     *
     * @param severity   lọc theo mức độ, {@code null} = mọi mức
     * @param unresolved {@code true} chỉ hồ sơ chưa {@code resolved_at}, {@code false} chỉ hồ sơ
     *                   đã khắc phục, {@code null} = cả hai
     */
    List<SecurityIncident> findForAdmin(IncidentSeverity severity, Boolean unresolved, int offset, int limit);

    long countForAdmin(IncidentSeverity severity, Boolean unresolved);
}
