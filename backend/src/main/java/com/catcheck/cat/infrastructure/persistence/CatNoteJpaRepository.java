package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.NoteType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CatNoteJpaRepository extends JpaRepository<CatNote, UUID> {

    Optional<CatNote> findByIdAndCatId(UUID id, UUID catId);

    Optional<CatNote> findById(UUID id);

    @Query("""
            SELECT n FROM CatNote n
            WHERE n.catId = :catId
              AND n.deletedAt IS NULL
              AND (:typeFilter IS NULL OR n.noteType = :typeFilter)
            ORDER BY n.occurredOn DESC NULLS LAST, n.createdAt DESC
            """)
    List<CatNote> findByCatId(
            @Param("catId") UUID catId,
            @Param("typeFilter") NoteType typeFilter,
            Pageable pageable);

    @Query("""
            SELECT COUNT(n) FROM CatNote n
            WHERE n.catId = :catId
              AND n.deletedAt IS NULL
              AND (:typeFilter IS NULL OR n.noteType = :typeFilter)
            """)
    long countByCatId(@Param("catId") UUID catId, @Param("typeFilter") NoteType typeFilter);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CatNote n SET n.deletedAt = :now, n.updatedAt = :now WHERE n.catId = :catId AND n.deletedAt IS NULL")
    int softDeleteAllByCatId(@Param("catId") UUID catId, @Param("now") Instant now);
}
