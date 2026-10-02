package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface ScanImageJpaRepository extends JpaRepository<ScanImage, UUID> {

    Optional<ScanImage> findByScanId(UUID scanId);
}
