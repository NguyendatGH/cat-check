package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatHealthSurvey;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Spring Data repository cho {@code cat_health_survey} (D14/D15, p4 C3). */
interface CatHealthSurveyJpaRepository extends JpaRepository<CatHealthSurvey, UUID> {

    Optional<CatHealthSurvey> findFirstByCatIdOrderByCreatedAtDesc(UUID catId);

    @Query("SELECT s FROM CatHealthSurvey s WHERE s.catId = :catId ORDER BY s.createdAt DESC")
    List<CatHealthSurvey> findHistoryByCatId(@Param("catId") UUID catId, Pageable pageable);
}
