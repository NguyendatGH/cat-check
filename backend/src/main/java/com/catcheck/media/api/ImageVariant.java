package com.catcheck.media.api;

/**
 * Biến thể phái sinh của một ảnh đã lưu (ảnh thu nhỏ, bản xem trước cho PDF…). Biến thể KHÔNG
 * có bản ghi riêng trong DB — nó chỉ là tệp cùng thư mục với tệp cha, đặt tên theo hậu tố
 * {@code -<suffix>} (p4 §4.3 K5: media cố ý không có bảng {@code stored_file}).
 *
 * <p>Vì không có bản ghi, {@link #deleteVariants(StorageKey)} phải xoá theo mẫu tên tiều tố
 * chứ không theo danh sách khoá — đó là lý do hậu tố ở đây là hằng số chuẩn hoá, không tuỳ ý.</p>
 */
public enum ImageVariant {

    /** Bản thu nhỏ cho danh sách mèo và ô chọn mèo khi quét. */
    THUMBNAIL("thumb"),

    /** Bản vừa đủ để render vào PDF mà không nhân bản ảnh gốc. */
    PRINT("print");

    private final String suffix;

    ImageVariant(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }
}
