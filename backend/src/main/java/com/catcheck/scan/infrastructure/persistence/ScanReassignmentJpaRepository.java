package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanReassignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface ScanReassignmentJpaRepository extends JpaRepository<ScanReassignment, UUID> {

    List<ScanReassignment> findByScanIdOrderByChangedAtDesc(UUID scanId);

    long countByScanId(UUID scanId);
}
