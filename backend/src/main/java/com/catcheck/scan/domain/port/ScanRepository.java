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
}
