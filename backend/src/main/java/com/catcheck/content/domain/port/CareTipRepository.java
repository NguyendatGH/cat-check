package com.catcheck.content.domain.port;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code care_tip} mà tầng application dùng.
 *
 * <p>Cổng này nằm ở {@code domain} và KHÔNG phải là Spring Data repository: kiểu duy nhất được
 * phép kế thừa {@code JpaRepository} nằm ở {@code content.infrastructure.persistence} (R7). Tầng
 * application chỉ biết interface này, nên không vô tình phụ thuộc Spring Data (R2).</p>
 */
public interface CareTipRepository {

    Optional<CareTip> findById(UUID id);

    /**
     * Danh sách công khai: chỉ bài {@link CareTipStatus#PUBLISHED} và chưa xoá mềm, đúng truy vấn
     * mà index {@code idx_care_tip_listing} phục vụ.
     */
    List<CareTip> findPublished(String locale, CareTipCategory category, CareTipKind kind, int limit);

    /** Bản đầy đủ theo slug + locale cho F8. Không lọc trạng thái — tầng application quyết định. */
    Optional<CareTip> findBySlugAndLocale(String slug, String locale);

    /** L40: mọi trạng thái, có phân trang kiểu offset (admin cần "trang 7/23"). */
    List<CareTip> findAllForAdmin(String locale, CareTipStatus status, int page, int size);

    long countForAdmin(String locale, CareTipStatus status);

    /** L43: hàng đợi duyệt, đúng thứ tự index {@code idx_care_tip_review_queue}. */
    List<CareTip> findReviewQueue(int limit);

    boolean existsBySlugAndLocale(String slug, String locale);

    CareTip save(CareTip careTip);

    /** Xoá mềm hàng loạt — dùng cho DSAR, không dùng {@code DELETE} vật lý. */
    void softDeleteAll(Collection<UUID> ids, java.time.Instant now);
}
