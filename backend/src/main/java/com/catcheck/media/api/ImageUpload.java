package com.catcheck.media.api;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Objects;

/**
 * Nội dung ảnh sắp ghi xuống storage.
 *
 * <p>Cố tình KHÔNG dùng {@link MultipartFile}: đó là kiểu của web layer (p7 §7.2.5 — tầng
 * {@code application} không được dùng HTTP/Servlet type), nên controller ở tầng {@code api}
 * phải chuyển sang record này trước khi gọi xuống {@code application}.</p>
 *
 * @param content      luồng nội dung ảnh; caller đóng, KHÔNG đóng giúp
 * @param filename     tên file gốc do client gửi — chỉ dùng để suy ra phần mở rộng
 * @param contentType  MIME type do client khai báo
 * @param sizeBytes    kích thước khai báo, dùng để kiểm tra trước khi ghi
 */
public record ImageUpload(
        InputStream content,
        String filename,
        String contentType,
        long sizeBytes
) {

    public ImageUpload {
        Objects.requireNonNull(content, "imageUpload.content phải có giá trị");
        Objects.requireNonNull(contentType, "imageUpload.contentType phải có giá trị");
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("imageUpload.sizeBytes không được âm");
        }
    }
}
