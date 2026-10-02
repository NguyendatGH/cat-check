package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.ChartCalibrationJob;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code chart_calibration_job}. Job thật là M7 — hiện chỉ có entity + cổng.
 */
public interface ChartCalibrationJobRepository {

    Optional<ChartCalibrationJob> findById(UUID id);

    ChartCalibrationJob save(ChartCalibrationJob job);
}
