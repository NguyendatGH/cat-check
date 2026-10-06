package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanAnalysis;
import com.catcheck.scan.domain.port.ScanAnalysisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
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

    @Override
    public List<ScanAnalysis> findBackfillCandidates(Instant computedFrom, String engineVersionPrefix,
                                                     int limit, int offset) {
        // PageRequest nhận (page, size) chứ không phải (offset, size) — job luôn đi theo lô cố
        // định nên offset là bội của limit và phép chia này không mất dòng nào.
        int size = Math.max(1, limit);
        return jpaRepository.findBackfillCandidates(computedFrom, engineVersionPrefix,
                PageRequest.of(offset / size, size));
    }

    @Override
    public long countBackfillCandidates(Instant computedFrom, String engineVersionPrefix) {
        return jpaRepository.countBackfillCandidates(computedFrom, engineVersionPrefix);
    }
}
