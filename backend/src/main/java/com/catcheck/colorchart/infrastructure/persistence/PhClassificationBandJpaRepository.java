package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.PhClassificationBand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code ph_classification_band} (R7).
 */
interface PhClassificationBandJpaRepository extends JpaRepository<PhClassificationBand, UUID> {

    List<PhClassificationBand> findByChartIdIsNullAndActiveTrueOrderBySortOrder();

    List<PhClassificationBand> findByChartIdOrderBySortOrder(UUID chartId);

    Optional<PhClassificationBand> findByCode(String code);
}
