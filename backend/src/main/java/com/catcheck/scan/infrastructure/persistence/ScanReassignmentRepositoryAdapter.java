package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanReassignment;
import com.catcheck.scan.domain.port.ScanReassignmentRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
class ScanReassignmentRepositoryAdapter implements ScanReassignmentRepository {

    private final ScanReassignmentJpaRepository jpaRepository;

    ScanReassignmentRepositoryAdapter(ScanReassignmentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ScanReassignment save(ScanReassignment reassignment) {
        return jpaRepository.save(reassignment);
    }

    @Override
    public List<ScanReassignment> findByScanIdOrderByChangedAtDesc(UUID scanId) {
        return jpaRepository.findByScanIdOrderByChangedAtDesc(scanId);
    }

    @Override
    public long countByScanId(UUID scanId) {
        return jpaRepository.countByScanId(scanId);
    }
}
