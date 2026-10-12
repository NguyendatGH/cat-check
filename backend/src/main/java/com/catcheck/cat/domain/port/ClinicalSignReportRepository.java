package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatClinicalSignReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClinicalSignReportRepository {

    Optional<CatClinicalSignReport> findById(UUID id);

    List<CatClinicalSignReport> findByCatId(UUID catId, int limit);

    long countByCatId(UUID catId);

    List<CatClinicalSignReport> findUnacknowledged(int limit);

    CatClinicalSignReport save(CatClinicalSignReport report);
}
