package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.ReferenceCardLayout;

import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc {@code reference_card_layout}.
 */
public interface ReferenceCardLayoutRepository {

    Optional<ReferenceCardLayout> findById(UUID id);

    Optional<ReferenceCardLayout> findActiveByCode(String code);
}
