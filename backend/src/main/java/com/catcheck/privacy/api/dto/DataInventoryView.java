package com.catcheck.privacy.api.dto;

import java.util.List;

/**
 * Một mục trong bảng kiểm kê dữ liệu — response C5, render ĐỘNG từ
 * {@code data_inventory_item} (p15 §15.4.3).
 *
 * @param code               'D1'…'D21'
 * @param category           nhóm hiển thị (đã theo locale)
 * @param description        mô tả cho người thường đọc (đã theo locale)
 * @param sensitivity        BASIC/SENSITIVE
 * @param legalBasis         căn cứ xử lý
 * @param purposes           các mục đích liên quan
 * @param retentionPolicyCode mã chính sách retention áp dụng
 * @param storageLocation    nơi lưu trữ
 * @param crossBorder        true = chuyển ra nước ngoài
 * @param recipient          bên thứ ba nhận dữ liệu
 * @param retentionDays      số ngày lưu theo retention_policy; vắng khi chưa có chính sách hoặc theo vòng đời tài khoản
 */
public record DataInventoryView(
        String code,
        String category,
        String description,
        String sensitivity,
        String legalBasis,
        List<String> purposes,
        String retentionPolicyCode,
        String storageLocation,
        boolean crossBorder,
        String recipient,
        Integer retentionDays
) {
}
