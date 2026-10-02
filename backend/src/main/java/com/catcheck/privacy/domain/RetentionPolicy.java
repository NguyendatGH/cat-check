package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Cấu hình thời hạn lưu trữ — dòng {@code retention_policy} (p4 B6, p15 REQ-RET-03).
 *
 * <p>Thời hạn <b>không hard-code</b> trong code để DPO/luật sư chỉnh mà không cần deploy.
 * Hai giá trị không được NỚI bằng bảng này vì là cam kết với owner/user: SCAN_IMAGE = 14
 * ngày và grace xoá tài khoản 7 ngày — service chặn giá trị lớn hơn (bất biến I15).
 * Chỉ vai trò DPO được sửa (p11 §11.5.1).</p>
 *
 * @param code                 mã chính sách (PK), xuất hiện trong job_run.job_name
 * @param dataInventoryCode    nối sang data_inventory_item(code) — trang "Dữ liệu của bạn"
 *                             và bảng retention là MỘT nguồn
 * @param targetTable          bảng bị tác động
 * @param retentionDays        null = "theo vòng đời tài khoản" (không có mốc tuyệt đối)
 * @param anchorColumn         cột mốc để tính hạn (created_at, resolved_at, uploaded_at…)
 * @param actionOnExpiry       HARD_DELETE/ANONYMIZE/ARCHIVE/MASK
 * @param jobName              job thực thi (khớp tên ở p12 §12.6 / p15 §15.5.1)
 * @param safetyThresholdPercent một lần chạy định tác động > ngưỡng ⇒ dừng và cảnh báo (REQ-RET-02)
 * @param enabled              false = tạm ngừng áp dụng
 * @param legalBasis           điều luật làm căn cứ — trả lời "vì sao 5 năm" ngay trong dữ liệu
 * @param updatedBy            người sửa gần nhất (DPO)
 * @param createdAt            mốc tạo
 */
public record RetentionPolicy(
        String code,
        String dataInventoryCode,
        String targetTable,
        Integer retentionDays,
        String anchorColumn,
        RetentionAction actionOnExpiry,
        String jobName,
        int safetyThresholdPercent,
        boolean enabled,
        String legalBasis,
        UUID updatedBy,
        Instant createdAt
) {

    public RetentionPolicy {
        if (code == null || targetTable == null || anchorColumn == null || actionOnExpiry == null) {
            throw new IllegalArgumentException("retentionPolicy thiếu trường bắt buộc");
        }
        if (retentionDays != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retentionPolicy.retentionDays phải > 0 khi NOT NULL (ck_retention_policy_days)");
        }
        if (safetyThresholdPercent < 1 || safetyThresholdPercent > 100) {
            throw new IllegalArgumentException("retentionPolicy.safetyThresholdPercent phải 1..100 (ck_retention_policy_threshold)");
        }
    }
}
