package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatClinicalSignReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/** Spring Data repository cho {@code cat_clinical_sign_report} (D20, p4 C5). */
interface ClinicalSignReportJpaRepository extends JpaRepository<CatClinicalSignReport, UUID> {

    @Query("SELECT r FROM CatClinicalSignReport r WHERE r.catId = :catId ORDER BY r.reportedAt DESC")
    List<CatClinicalSignReport> findByCatId(@Param("catId") UUID catId, Pageable pageable);

    long countByCatId(UUID catId);

    @Query("""
            SELECT r FROM CatClinicalSignReport r
            WHERE r.acknowledgedAt IS NULL
            ORDER BY r.reportedAt ASC
            """)
    List<CatClinicalSignReport> findUnacknowledged(Pageable pageable);
}
