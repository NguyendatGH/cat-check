package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PackagePlanAdminView;
import com.catcheck.credit.domain.PackagePlanUpdate;

import java.util.List;
import java.util.Optional;

/**
 * Cổng đọc/ghi cấu hình gói {@code package_plan}.
 *
 * <p>Bảng này do module cat/content tạo ở {@code V7__catalog.sql} — module credit không chèn
 * dòng nào, nhưng từ L26 ({@code PATCH /admin/package-plans/{code}}) thì có SỬA: p11 §11.5.4
 * giao {@code package_plan} cho {@code ADMIN_SUPER}, và màn quản trị gói nằm trong phạm vi
 * module credit vì mọi hệ quả của nó ({@code credit_amount}, {@code credit_validity_days},
 * {@code features}) là nghiệp vụ credit. Không có {@code @ManyToOne} trỏ sang bảng này: R6 cấm
 * JPA association trỏ ra ngoài module, quan hệ chỉ là {@code package_code VARCHAR(32)}.</p>
 */
public interface PackagePlanPort {

    Optional<PackagePlan> findByCode(String code);

    /**
     * F3 {@code GET /reference/packages} — các gói đang bán ({@code active = true}), sắp xếp
     * theo {@code weight_kg} tăng dần để danh mục hiển thị từ gói nhỏ tới gói lớn.
     */
    List<PackagePlan> findAllActive();

    /**
     * L25 — mọi gói cho màn admin, kèm {@code updated_at} để sinh ETag.
     *
     * @param includeInactive {@code false} = chỉ gói đang bán (mặc định của màn, p14 §14.3.2
     *                        mục 5); {@code true} = cả gói đã ngừng bán
     */
    List<PackagePlanAdminView> findAllForAdmin(boolean includeInactive);

    /** L26 — một gói kèm {@code updated_at}, để kiểm {@code If-Match} trước khi ghi. */
    Optional<PackagePlanAdminView> findForAdmin(String code);

    /**
     * L26 — ghi các trường được gửi trong {@code update} và <b>tăng {@code version} lên 1</b>.
     *
     * <p>Tăng {@code version} là bắt buộc, không phải tuỳ chọn: {@code credit_batch} chụp
     * {@code package_version} tại thời điểm kích hoạt (p5 R1), nên hai cấu hình khác nhau mà
     * cùng số version sẽ làm mọi đối soát về sau không phân biệt được.</p>
     *
     * @return {@code true} nếu có đúng một dòng được ghi
     */
    boolean update(String code, PackagePlanUpdate update);
}
