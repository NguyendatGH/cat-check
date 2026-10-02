package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.PhClassificationBand;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi {@code ph_classification_band}.
 */
public interface PhClassificationBandRepository {

    /**
     * Dải toàn cục ({@code chartId = null}) đang {@code active}, sắp theo {@code sortOrder}.
     * Đây là nguồn duy nhất cho {@code GET /reference/ph-bands} (F1) và cho S10.
     */
    List<PhClassificationBand> findGlobalActiveOrderBySortOrder();

    List<PhClassificationBand> findByChartIdOrderBySortOrder(UUID chartId);

    Optional<PhClassificationBand> findByCode(String code);

    Optional<PhClassificationBand> findById(UUID id);

    PhClassificationBand save(PhClassificationBand band);
}
