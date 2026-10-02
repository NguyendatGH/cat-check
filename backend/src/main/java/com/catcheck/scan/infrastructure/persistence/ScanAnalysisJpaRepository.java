package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ScanAnalysisJpaRepository extends JpaRepository<ScanAnalysis, UUID> {

    Optional<ScanAnalysis> findByScanIdAndCurrentTrue(UUID scanId);

    List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId);
}
