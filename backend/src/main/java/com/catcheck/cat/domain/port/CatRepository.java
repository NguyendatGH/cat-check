package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi bảng {@code cat} mà tầng application dùng.
 *
 * <p>Cổng này nằm ở {@code domain} và KHÔNG phải Spring Data repository: kiểu duy nhất được phép
 * kế thừa {@code JpaRepository} nằm ở {@code cat.infrastructure.persistence} (R7). Tầng application
 * chỉ biết interface này nên không vô tình phụ thuộc Spring Data (R2).</p>
 *
 * <p>Mọi truy vấn đều nhận {@code ownerId} và tự lọc trong điều kiện, KHÔNG lọc ở tầng application sau
 * khi đã nạp: nếu không lọc trong SQL thì một con mèo của người khác vẫn có thể bị trả về rồi mới bị
 * từ chối, tức là dữ liệu đã lọt ra khỏi tầng persistence một lần.</p>
 */
public interface CatRepository {

    Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId);

    /**
     * D1 — danh sách mèo của một chủ. Mặc định lấy {@code ACTIVE}; {@code includeArchived} mở thêm
     * hồ sơ đã gỡ để chủ vẫn xem được lịch sử mà không phải chuyển từng bé qua D6.
     */
    List<Cat> findAllByOwnerId(UUID ownerId, CatStatus statusFilter, boolean includeArchived);

    /** C31: đếm số hồ sơ đang hoạt động để so với trần entitlement và trần cứng. */
    long countActiveByOwnerId(UUID ownerId);

    /**
     * D8 — trả về mèo chính hiện tại, hoặc {@code null} nếu chủ chưa chọn bé nào. Truy vấn này dùng
     * partial unique index {@code uq_cat_owner_primary}.
     */
    Optional<Cat> findPrimaryByOwnerId(UUID ownerId);

    /** D8 — hạ cờ mèo chính cũ trong một lệnh, trước khi nâng mèo chính mới. */
    void clearPrimaryForOwner(UUID ownerId, Instant now);

    /**
     * Đảm bảo {@code publicCode} chưa có: mã này người dùng gõ tay, nên va chạm là thật.
     * Adapter loop thử lại bằng entropy mới cho tới khi vừa, thay vì hy vọng may mắn.
     */
    boolean existsByPublicCode(String publicCode);

    Cat save(Cat cat);
}
