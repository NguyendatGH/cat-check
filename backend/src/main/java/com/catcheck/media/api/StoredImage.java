package com.catcheck.media.api;

import java.util.Objects;

/**
 * Kết quả ghi ảnh thành công — chỉ trả về khoá + provider, TUYỆT ĐỐI không trả URL tới hạn dài.
 * URL có hạn phải xin riêng qua {@link ImageStorage#presignedUrl(StorageKey, java.time.Duration)}
 * đúng lúc cần (p6 §6.11.2: không để lộ đường dẫn tệp tuyệt đối).
 *
 * @param key         khoá lưu trữ
 * @param provider    {@code LOCAL} hoặc {@code CLOUDINARY} — ghi vào cột
 *                    {@code *.avatar_storage_provider} của bảng chủ sở hữu
 * @param sizeBytes   kích thước thực tế sau khi ghi
 * @param contentType MIME type đã xác nhận
 */
public record StoredImage(
        StorageKey key,
        String provider,
        long sizeBytes,
        String contentType
) {

    public StoredImage {
        Objects.requireNonNull(key, "storedImage.key phải có giá trị");
        Objects.requireNonNull(provider, "storedImage.provider phải có giá trị");
    }
}
