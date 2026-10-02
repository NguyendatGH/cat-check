package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.PackagePlan;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc cấu hình gói {@code package_plan}.
 *
 * <p>Bảng này do module cat/content tạo ở {@code V7__catalog.sql} — module credit chỉ ĐỌC và
 * không có bản ghi nào trong đó. Không có {@code @ManyToOne} trỏ sang nó: R6 cấm JPA association
 * trỏ ra ngoài module, nên quan hệ chỉ là {@code package_code VARCHAR(32)} + cổng này.</p>
 */
public interface PackagePlanPort {

    Optional<PackagePlan> findByCode(String code);

    /**
     * F3 {@code GET /reference/packages} — các gói đang bán ({@code active = true}), sắp xếp
     * theo {@code weight_kg} tăng dần để danh mục hiển thị từ gói nhỏ tới gói lớn.
     */
    List<PackagePlan> findAllActive();
}
