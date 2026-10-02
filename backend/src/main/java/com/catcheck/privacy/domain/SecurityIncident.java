package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ sự cố lộ/mất dữ liệu — dòng {@code security_incident} (p4 B9, p15 REQ-INC-01
 * + Đ29.1.c NĐ356: giữ ≥ 5 năm).
 *
 * <p>Từ {@code HIGH} trở lên bắt buộc thông báo cơ quan quản lý trong 72 giờ kể từ
 * {@code detectedAt}. Job retention không được xoá trước {@code retainUntil}. Bị chặn
 * DELETE ở DB nhưng vẫn UPDATE được (p4 §4.1.2).</p>
 *
 * @param id                  UUID v7
 * @param publicRef           'INC-2026-0007' — UNIQUE
 * @param severity            LOW/MEDIUM/HIGH/CRITICAL
 * @param category            DATA_BREACH/DATA_LOSS/UNAUTHORIZED_ACCESS/AVAILABILITY/OTHER
 * @param summary             mô tả ngắn
 * @param affectedSubjectCount số chủ thể dữ liệu bị ảnh hưởng (ước tính ban đầu và cập nhật)
 * @param affectedDataCodes    mã data_inventory_item bị ảnh hưởng
 * @param detectedAt          mốc bắt đầu đồng hồ 72 giờ báo cáo
 * @param classifiedAt        mốc phân loại lại severity
 * @param containedAt         mốc khống chế xong
 * @param authorityNotifiedAt mốc báo cơ quan quản lý (A05)
 * @param subjectsNotifiedAt  mốc báo chủ thể dữ liệu
 * @param resolvedAt          mốc khắc phục xong
 * @param retainUntil         resolved_at + 5 năm — job retention không xoá trước mốc này
 * @param handledBy           DPO xử lý — FK app_user(id)
 * @param reportRef           storage key của hồ sơ đầy đủ
 * @param createdAt           mốc tạo
 */
public record SecurityIncident(
        UUID id,
        String publicRef,
        IncidentSeverity severity,
        IncidentCategory category,
        String summary,
        Integer affectedSubjectCount,
        List<String> affectedDataCodes,
        Instant detectedAt,
        Instant classifiedAt,
        Instant containedAt,
        Instant authorityNotifiedAt,
        Instant subjectsNotifiedAt,
        Instant resolvedAt,
        Instant retainUntil,
        UUID handledBy,
        String reportRef,
        Instant createdAt
) {

    public SecurityIncident {
        if (id == null || publicRef == null || severity == null || category == null || summary == null) {
            throw new IllegalArgumentException("securityIncident thiếu trường bắt buộc");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("securityIncident.detectedAt bắt buộc (đồng hồ 72 giờ)");
        }
        affectedDataCodes = affectedDataCodes == null ? List.of() : List.copyOf(affectedDataCodes);
    }

    /** Còn trong 72 giờ bắt buộc báo cơ quan quản lý chưa? (severity HIGH/CRITICAL). */
    public boolean isAuthorityNotificationOverdue(Instant now) {
        boolean highSeverity = severity == IncidentSeverity.HIGH || severity == IncidentSeverity.CRITICAL;
        return highSeverity && authorityNotifiedAt == null
                && detectedAt.plusSeconds(72 * 3600).isBefore(now);
    }
}
