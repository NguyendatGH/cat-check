package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ScanAnalysisRepositoryAdapter implements ScanAnalysisRepository {

    private final ScanAnalysisJpaRepository jpaRepository;

    ScanAnalysisRepositoryAdapter(ScanAnalysisJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ScanAnalysis save(ScanAnalysis analysis) {
        return jpaRepository.save(analysis);
    }

    @Override
    public Optional<ScanAnalysis> findCurrentByScanId(UUID scanId) {
        return jpaRepository.findByScanIdAndCurrentTrue(scanId);
    }

    @Override
    public List<ScanAnalysis> findByScanIdOrderByComputedAtDesc(UUID scanId) {
        return jpaRepository.findByScanIdOrderByComputedAtDesc(scanId);
    }

    @Override
    public Optional<ScanAnalysis> findById(UUID id) {
        return jpaRepository.findById(id);
    }
}
