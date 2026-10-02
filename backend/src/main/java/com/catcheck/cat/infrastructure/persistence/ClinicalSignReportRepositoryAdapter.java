package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatClinicalSignReport;
import com.catcheck.cat.domain.port.ClinicalSignReportRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ClinicalSignReportRepositoryAdapter implements ClinicalSignReportRepository {

    private final ClinicalSignReportJpaRepository jpaRepository;

    ClinicalSignReportRepositoryAdapter(ClinicalSignReportJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CatClinicalSignReport> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<CatClinicalSignReport> findByCatId(UUID catId, int limit) {
        return jpaRepository.findByCatId(catId, PageRequest.of(0, Math.max(limit, 1)));
    }

    @Override
    public long countByCatId(UUID catId) {
        return jpaRepository.countByCatId(catId);
    }

    @Override
    public List<CatClinicalSignReport> findUnacknowledged(int limit) {
        return jpaRepository.findUnacknowledged(PageRequest.of(0, Math.max(limit, 1)));
    }

    @Override
    public CatClinicalSignReport save(CatClinicalSignReport report) {
        return jpaRepository.save(report);
    }
}
