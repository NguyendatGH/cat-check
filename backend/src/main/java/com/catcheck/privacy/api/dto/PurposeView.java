package com.catcheck.privacy.api.dto;

/**
 * Một mục đích xử lý đã resolve theo locale — response C1 (p8 §8.4.3).
 *
 * @param code            mã mục đích (nguyên văn trong DB/API)
 * @param label           câu hiển thị cạnh checkbox (đã theo locale)
 * @param description     mô tả loại dữ liệu + mục đích (đã theo locale)
 * @param mandatory       true = bắt buộc (SERVICE_CORE)
 * @param sensitive       true = UI bắt buộc hiện nhãn "Dữ liệu nhạy cảm" (Đ6.4 NĐ356)
 * @param defaultState    trạng thái mặc định của ô tick — luôn false với mục tuỳ chọn (I17)
 * @param withdrawEffect  câu giải thích "nếu từ chối thì sao"
 * @param phase           1 / 2 / 3
 */
public record PurposeView(
        String code,
        String label,
        String description,
        boolean mandatory,
        boolean sensitive,
        boolean defaultState,
        String withdrawEffect,
        int phase
) {
}
