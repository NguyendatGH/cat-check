package com.catcheck.content.infrastructure.persistence;

import com.catcheck.content.domain.CareTip;
import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.CareTipStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code care_tip}.
 *
 * <p>Nằm ở {@code infrastructure.persistence} vì R7 chỉ cho phép class trong package này phụ thuộc
 * {@code org.springframework.data..} và {@code jakarta.persistence.criteria..}. Tầng application
 * không được biết tới interface này — nó chỉ biết {@code CareTipRepository} ở
 * {@code content.domain.port} (R2).</p>
 *
 * <p>Mọi truy vấn đều lọc {@code deleted_at IS NULL} ở tầng DB, không lọc ở tầng Java: nếu lọc
 * sau khi lấy, trang cuối sẽ thiếu bản ghi và client phải load nhiều hơn một vòng.</p>
 */
interface CareTipJpaRepository extends JpaRepository<CareTip, UUID> {

    @Query("""
            SELECT t FROM CareTip t
            WHERE t.deletedAt IS NULL
              AND t.status = com.catcheck.content.domain.CareTipStatus.PUBLISHED
              AND t.locale = :locale
              AND (:category IS NULL OR t.category = :category)
              AND (:kind IS NULL OR t.kind = :kind)
            ORDER BY t.sortWeight ASC, t.publishedAt DESC
            """)
    List<CareTip> findPublished(
            @Param("locale") String locale,
            @Param("category") CareTipCategory category,
            @Param("kind") CareTipKind kind,
            Pageable pageable);

    @Query("SELECT t FROM CareTip t WHERE t.slug = :slug AND t.locale = :locale AND t.deletedAt IS NULL")
    Optional<CareTip> findBySlugAndLocale(@Param("slug") String slug, @Param("locale") String locale);

    @Query("""
            SELECT t FROM CareTip t
            WHERE t.deletedAt IS NULL
              AND (:locale IS NULL OR t.locale = :locale)
              AND (:status IS NULL OR t.status = :status)
            ORDER BY t.updatedAt DESC
            """)
    List<CareTip> findAllForAdmin(
            @Param("locale") String locale,
            @Param("status") CareTipStatus status,
            Pageable pageable);

    @Query("""
            SELECT COUNT(t) FROM CareTip t
            WHERE t.deletedAt IS NULL
              AND (:locale IS NULL OR t.locale = :locale)
              AND (:status IS NULL OR t.status = :status)
            """)
    long countForAdmin(@Param("locale") String locale, @Param("status") CareTipStatus status);

    @Query("""
            SELECT t FROM CareTip t
            WHERE t.deletedAt IS NULL AND t.status = com.catcheck.content.domain.CareTipStatus.IN_REVIEW
            ORDER BY t.updatedAt ASC
            """)
    List<CareTip> findReviewQueue(Pageable pageable);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM CareTip t
            WHERE t.slug = :slug AND t.locale = :locale
            """)
    boolean existsBySlugAndLocale(@Param("slug") String slug, @Param("locale") String locale);

    @Query("UPDATE CareTip t SET t.deletedAt = :now, t.updatedAt = :now WHERE t.id IN :ids AND t.deletedAt IS NULL")
    int softDeleteAll(@Param("ids") List<UUID> ids, @Param("now") Instant now);
}
