package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.Scan;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi AGGREGATE {@code scan} (thao tác theo id, không phải truy vấn danh sách — xem
 * {@link ScanQueryRepository} cho các câu hỏi hiển thị/thống kê cần JOIN {@code scan_analysis}).
 * Tầng {@code application} chỉ biết interface này (R2), hiện thực ở
 * {@code scan.infrastructure.persistence} (R7).
 */
public interface ScanRepository {

    Scan save(Scan scan);

    Optional<Scan> findById(UUID id);

    /** Idempotency: mỗi {@code (user_id, idempotency_key)} chỉ có tối đa một dòng (p5 R8). */
    Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    /**
     * Đẩy mọi lệnh ghi đang chờ của transaction hiện tại xuống DB <b>ngay</b>, không đợi commit.
     *
     * <p>Vì sao cổng này tồn tại: {@code ScanSavedEvent} được xử lý ĐỒNG BỘ, CÙNG TRANSACTION
     * (p7 §7.4.2 + §7.4.5), và listener của {@code insight} đọc lịch sử qua
     * {@link ScanQueryRepository} — tức bằng SQL thô trên cùng connection. Lệnh ghi của tầng
     * ORM chỉ xuống DB lúc commit, nên nếu không đẩy trước thì câu SQL đó <b>không nhìn thấy
     * chính scan vừa tạo</b>: rule R1–R4 đếm thiếu đúng một mẫu và cảnh báo chỉ bắn ở lần quét
     * kế tiếp (H15.75 — đo thật: cặp confidence 0.45/0.45 không sinh flag).</p>
     *
     * <p>Đây KHÔNG phải commit: dữ liệu vẫn nằm trong transaction, rollback vẫn cuốn sạch, nên
     * bất biến "health_flag cùng số phận với scan" của p7 §7.4.2 được giữ nguyên.</p>
     */
    void flushPendingWrites();
}
