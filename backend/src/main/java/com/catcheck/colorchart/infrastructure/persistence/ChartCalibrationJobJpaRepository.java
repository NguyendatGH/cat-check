package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ChartCalibrationJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Spring Data repository cho {@code chart_calibration_job} (R7).
 */
interface ChartCalibrationJobJpaRepository extends JpaRepository<ChartCalibrationJob, UUID> {
}
