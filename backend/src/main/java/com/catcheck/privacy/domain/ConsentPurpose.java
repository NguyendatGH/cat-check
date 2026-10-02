package com.catcheck.privacy.domain;

import java.time.Instant;

/**
 * Một mục đích xử lý dữ liệu — dòng {@code consent_purpose} (p4 B3).
 *
 * <p>Đây là <b>cấu hình</b>, không hard-code trong code (quyết định #5b, TD-04): mỗi dòng là
 * <b>một checkbox riêng</b> trong UI (p15 §15.3.2). Bất biến I17 ép ở DB: mục đích tuỳ chọn
 * không bao giờ {@code default_state = true}.</p>
 *
 * @param code            mã nguyên văn xuất hiện trong API/UI/consent_record (PK)
 * @param labelVy         câu hiển thị cạnh checkbox (tiếng Việt)
 * @param labelEn         câu hiển thị (tiếng Anh), null khi chưa dịch
 * @param descriptionVy   mô tả loại dữ liệu + mục đích (Đ9.2.a NĐ356 bắt buộc nêu cả hai)
 * @param descriptionEn   mô tả tiếng Anh
 * @param isMandatory     true chỉ cho SERVICE_CORE (P1) và ORDER_FULFILLMENT (P3)
 * @param isSensitiveData true ⇒ UI bắt buộc hiện nhãn "Dữ liệu nhạy cảm" (Đ6.4 NĐ356)
 * @param defaultState    trạng thái mặc định của ô tick — luôn false với mục tuỳ chọn
 * @param phase           1 / 2 / 3
 * @param displayOrder    thứ tự hiển thị trong Trung tâm quyền riêng tư
 * @param active          false = ngừng dùng, KHÔNG xoá (consent cũ còn trỏ tới)
 * @param withdrawEffect  câu giải thích "nếu từ chối thì sao" (p15 §15.3.2)
 * @param createdAt       mốc tạo bản ghi cấu hình
 */
public record ConsentPurpose(
        String code,
        String labelVy,
        String labelEn,
        String descriptionVy,
        String descriptionEn,
        boolean isMandatory,
        boolean isSensitiveData,
        boolean defaultState,
        int phase,
        int displayOrder,
        boolean active,
        String withdrawEffect,
        Instant createdAt
) {

    public ConsentPurpose {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("consentPurpose.code không được trống");
        }
        if (labelVy == null || descriptionVy == null) {
            throw new IllegalArgumentException("consentPurpose bắt buộc có label_vi và description_vi");
        }
        if (!isMandatory && defaultState) {
            throw new IllegalArgumentException(
                    "Mục đích tuỳ chọn không được tick sẵn (ck_consent_purpose_default_off): " + code);
        }
    }
}
