package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.ChartCalibrationJob;

import java.util.Optional;
import java.util.UUID;

public interface ChartCalibrationJobRepository {

    Optional<ChartCalibrationJob> findById(UUID id);

    ChartCalibrationJob save(ChartCalibrationJob job);
}
