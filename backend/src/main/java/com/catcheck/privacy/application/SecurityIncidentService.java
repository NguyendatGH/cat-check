package com.catcheck.privacy.application;

import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Quản lý hồ sơ sự cố lộ/mất dữ liệu (p4 B9, p15 REQ-INC-01 + Đ29.1.c NĐ356: giữ ≥ 5 năm).
 *
 * <p>API nằm ở nhóm admin L của p8 (M6) — ở M1 đây là service nội bộ để job/vận hành
 * ghi hồ sơ. {@code HIGH}/{@code CRITICAL} bắt buộc báo cơ quan quản lý trong 72 giờ
 * kể từ {@code detectedAt}; {@link #findOverdueAuthorityNotifications()} là nguồn dữ
 * liệu cho {@code IncidentDeadlineMonitorJob} (p12 §12.6.5).</p>
 */
@Service
public class SecurityIncidentService {

    private final SecurityIncidentPort incidentPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public SecurityIncidentService(SecurityIncidentPort incidentPort, UuidV7 uuidV7, Clock clock) {
        this.incidentPort = incidentPort;
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

    /** Cập nhật các mốc điều trị (classified/contained/authority/subjects/resolved/retain_until). */
    @Transactional
    public void updateIncident(SecurityIncident incident) {
        incidentPort.update(incident);
    }

    /** HIGH/CRITICAL chưa báo cơ quan quản lý quá 72 giờ — nguồn cho job giám sát (p4 B9). */
    public List<SecurityIncident> findOverdueAuthorityNotifications() {
        return incidentPort.findOverdueAuthorityNotification(clock.instant());
    }
}
