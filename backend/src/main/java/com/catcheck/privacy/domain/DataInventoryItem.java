package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;

/**
 * Một mục trong bảng kiểm kê dữ liệu cá nhân D1…D21 (p4 B10, p15 §15.2.2).
 *
 * <p>Khối "Dữ liệu CatCheck đang giữ về bạn" phải <b>render động từ bảng này</b>, không
 * phải văn bản chép tay — thêm một cột vào DB mà quên sửa văn bản là lỗi tuân thủ điển
 * hình (p15 §15.4.3).</p>
 *
 * @param code                 'D1'…'D21' (PK, mã đã đặt trong văn bản pháp lý)
 * @param categoryVi           nhóm hiển thị, ví dụ "Tài khoản", "Ảnh & kết quả quét"
 * @param descriptionVy        mô tả cho người thường đọc
 * @param descriptionEn        mô tả tiếng Anh
 * @param sensitivity          BASIC / SENSITIVE
 * @param legalBasis           căn cứ xử lý
 * @param purposeCodes          các consent_purpose.code liên quan
 * @param retentionPolicyCode  FK retention_policy(code) — nối thời hạn lưu
 * @param storageLocation      "PostgreSQL (VN)", "Cloudinary (Singapore)"…
 * @param crossBorder          true ⇒ trang riêng tư phải nói rõ bên nhận
 * @param recipient            bên thứ ba nhận dữ liệu
 * @param active               false = không còn thuộc phạm vi
 * @param createdAt            mốc tạo
 */
public record DataInventoryItem(
        String code,
        String categoryVi,
        String descriptionVi,
        String descriptionEn,
        InventorySensitivity sensitivity,
        LegalBasis legalBasis,
        List<String> purposeCodes,
        String retentionPolicyCode,
        String storageLocation,
        boolean crossBorder,
        String recipient,
        boolean active,
        Instant createdAt
) {

    public DataInventoryItem {
        if (code == null || categoryVi == null || descriptionVi == null) {
            throw new IllegalArgumentException("dataInventoryItem thiếu trường bắt buộc");
        }
        if (sensitivity == null || legalBasis == null || storageLocation == null) {
            throw new IllegalArgumentException(
                    "dataInventoryItem bắt buộc có sensitivity, legalBasis, storageLocation (p15 §15.4.3)");
        }
        purposeCodes = purposeCodes == null ? List.of() : List.copyOf(purposeCodes);
    }
}
