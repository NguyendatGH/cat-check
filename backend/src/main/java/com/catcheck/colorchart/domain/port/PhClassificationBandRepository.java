package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.PhClassificationBand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhClassificationBandRepository {

    List<PhClassificationBand> findGlobalActiveOrderBySortOrder();

    List<PhClassificationBand> findByChartIdOrderBySortOrder(UUID chartId);

    Optional<PhClassificationBand> findByCode(String code);

    Optional<PhClassificationBand> findById(UUID id);

    PhClassificationBand save(PhClassificationBand band);
}
