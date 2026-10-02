package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code cat}.
 *
 * <p>Nằm ở {@code infrastructure.persistence} (R7 — chỉ package này được phụ thuộc
 * {@code org.springframework.data..}). Tầng application chỉ biết
 * {@code cat.domain.port.CatRepository} (R2).</p>
 */
interface CatJpaRepository extends JpaRepository<Cat, UUID> {

    /**
     * KHÔNG lọc {@code deletedAt}: D5 cần phân biệt "không tồn tại/không sở hữu" (404
     * {@code CAT_NOT_FOUND}) với "đã xoá mềm rồi" (409 {@code CAT_ALREADY_DELETED}) — việc đó do
     * {@code CatProfileService} tự kiểm sau khi nạp, không phải ở tầng truy vấn.
     */
    Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            SELECT c FROM Cat c
            WHERE c.ownerId = :ownerId
              AND c.deletedAt IS NULL
              AND (c.status = :statusFilter OR (:includeArchived = true AND c.status = com.catcheck.cat.domain.CatStatus.ARCHIVED))
            ORDER BY c.primary DESC, c.name ASC
            """)
    List<Cat> findAllByOwnerId(
            @Param("ownerId") UUID ownerId,
            @Param("statusFilter") CatStatus statusFilter,
            @Param("includeArchived") boolean includeArchived);

    long countByOwnerIdAndStatusAndDeletedAtIsNull(UUID ownerId, CatStatus status);

    Optional<Cat> findByOwnerIdAndPrimaryTrueAndDeletedAtIsNull(UUID ownerId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Cat c SET c.primary = false, c.updatedAt = :now WHERE c.ownerId = :ownerId AND c.primary = true")
    int clearPrimaryForOwner(@Param("ownerId") UUID ownerId, @Param("now") Instant now);

    boolean existsByPublicCode(String publicCode);
}
