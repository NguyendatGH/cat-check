package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.PhClassificationBand;
import com.catcheck.colorchart.domain.port.PhClassificationBandRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link PhClassificationBandRepository} sang Spring Data (R2).
 */
@Repository
class PhClassificationBandRepositoryAdapter implements PhClassificationBandRepository {

    private final PhClassificationBandJpaRepository jpaRepository;

    PhClassificationBandRepositoryAdapter(PhClassificationBandJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PhClassificationBand> findGlobalActiveOrderBySortOrder() {
        return jpaRepository.findByChartIdIsNullAndActiveTrueOrderBySortOrder();
    }

    @Override
    public List<PhClassificationBand> findByChartIdOrderBySortOrder(UUID chartId) {
        return jpaRepository.findByChartIdOrderBySortOrder(chartId);
    }

    @Override
    public Optional<PhClassificationBand> findByCode(String code) {
        return jpaRepository.findByCode(code);
    }

    @Override
    public Optional<PhClassificationBand> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public PhClassificationBand save(PhClassificationBand band) {
        return jpaRepository.save(band);
    }
}
