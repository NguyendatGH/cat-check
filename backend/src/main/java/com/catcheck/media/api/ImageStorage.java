package com.catcheck.media.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Cổng trừu tượng hoá lưu trữ nhị phân (LOCAL / CLOUDINARY) — vai trò của module {@code media}
 * theo p7 §7.2.3: "Trừu tượng hoá lưu trữ nhị phân: {@code ImageStorage} (LOCAL / CLOUDINARY),
 * staging file tạm, sinh URL có TTL, xoá theo retention, xoá biến thể phái sinh".
 *
 * <p>Module media <b>không sở hữu bảng nào</b> (p4 §4.3 K5 cố ý không tạo {@code stored_file}) —
 * khoá lưu trữ nằm ngay trên bảng chủ sở hữu tệp, ví dụ {@code cat.avatar_storage_key}. Vì vậy
 * cổng này KHÔNG có {@code find}/{@code save} theo id; nó chỉ nhận khoá do module chủ sở hữu
 * tạo ra và trả lại khoá đã ghi.</p>
 *
 * <p>Mọi kiểu trong chữ ký đều nằm trong {@code media.api} để không module nào phải import từ
 * {@code media.domain} (p7 §7.3).</p>
 */
public interface ImageStorage {

    /** Tên provider, ghi vào cột {@code *_storage_provider} của bảng chủ sở hữu. */
    String provider();

    /**
     * Ghi ảnh vĩnh viễn, trả khoá đã lưu. Người gọi tự sinh {@code namespace} để tách vùng lưu
     * theo module (ví dụ {@code cat-avatar}) — media không tự đoán.
     */
    StoredImage put(String namespace, ImageUpload upload);

    /**
     * Ghi ảnh tạm, CHƯA sinh khoá vĩnh viễn. Bước staging tách riêng để ảnh nộp từ client không
     * bao giờ nằm trong đường dẫn mà module khác có thể đoán tên.
     *
     * @param ttl thời gian sống của bản tạm; hết hạn thì {@link #deleteExpiredStaging(Instant)} dọn
     */
    StagedUpload stage(String namespace, ImageUpload upload, Duration ttl);

    /**
     * Chốt một ảnh đã staging thành tệp vĩnh viễn. Ghi theo cơ chế ghi-tạm-rồi-đổi-tên nên
     * không bao giờ để lại tệp nửa vời.
     */
    StoredImage commit(StagedUpload staged);

    /**
     * Lưu biến thể phái sinh của một ảnh đã có. Việc TẠO nội dung biến thể (thu nhỏ, đổi khung)
     * do module gọi (pipeline `scan`) chịu trách nhiệm — pom chưa có thư viện xử lý ảnh, media chỉ
     * lo chỗ lưu và vòng đời. Xem {@code docs/handovers/A3.md}.
     */
    StoredImage putVariant(StorageKey parent, ImageVariant variant, ImageUpload upload);

    /** Mở nội dung tệp để đọc (avatar, ảnh gốc của scan). Rỗng nếu khoá không tồn tại. */
    Optional<java.io.InputStream> open(StorageKey key);

    /**
     * URL có hạn để trả ra ngoài. Với {@code LOCAL} mặc định là URL nội bộ qua proxy có token
     * chữ ký, KHÔNG phải đường dẫn tệp trên đĩa — không để lộ đường dẫn tuyệt đối (p8 §8.1.3).
     */
    java.net.URI presignedUrl(StorageKey key, Duration ttl);

    boolean exists(StorageKey key);

    /** Xoá tệp. Xoá cả các biến thể phái sinh kèm theo. Không ném lỗi nếu khoá đã biến mất. */
    void delete(StorageKey key);

    /**
     * Xoá các biến thể phái sinh mà không đụng tới tệp cha — dùng khi tệp cha còn sống nhưng
     * biến thể đã cũ (ví dụ đổi kích thước hiển thị).
     */
    void deleteVariants(StorageKey parent);

    /**
     * Xoá mọi tệp đã tạo trước mốc {@code cutoff} (job retention p15). Mặc định KHÔNG quét
     * tệp vĩnh viễn: đó là việc của module sở hữu dữ liệu, vì ba loại tệp có ba vòng đời khác
     * nhau (p7 §7.2.3).
     */
    void deleteOlderThan(Instant cutoff);

    /** Dọn các ảnh staging đã quá hạn. Trả về số tệp đã xoá. */
    int deleteExpiredStaging(Instant now);

    /** Liệt kê khoá đang có trong một namespace — phục vụ job retention và kiểm tra chẩn đoán. */
    Stream<StorageKey> list(String namespace);

    /**
     * Ảnh đã ghi tạm, chưa chốt.
     *
     * @param stagedKey khoá tạm (namespace staging riêng, không đụng khoá vĩnh viễn)
     * @param expiresAt mốc hết hạn
     */
    record StagedUpload(StorageKey stagedKey, String contentType, long sizeBytes, Instant expiresAt) {
    }
}
