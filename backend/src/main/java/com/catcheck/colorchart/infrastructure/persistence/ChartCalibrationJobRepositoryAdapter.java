package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ChartCalibrationJob;
import com.catcheck.colorchart.domain.port.ChartCalibrationJobRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

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
