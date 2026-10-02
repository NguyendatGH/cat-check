package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ChartCalibrationJob;
import com.catcheck.colorchart.domain.port.ChartCalibrationJobRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link ChartCalibrationJobRepository} sang Spring Data (R2).
 */
@Repository
class ChartCalibrationJobRepositoryAdapter implements ChartCalibrationJobRepository {

    private final ChartCalibrationJobJpaRepository jpaRepository;

    ChartCalibrationJobRepositoryAdapter(ChartCalibrationJobJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ChartCalibrationJob> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public ChartCalibrationJob save(ChartCalibrationJob job) {
        return jpaRepository.save(job);
    }
}
