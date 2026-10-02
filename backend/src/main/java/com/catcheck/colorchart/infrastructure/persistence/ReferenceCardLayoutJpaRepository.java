package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ReferenceCardLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data repository cho {@code reference_card_layout} (R7).
 */
interface ReferenceCardLayoutJpaRepository extends JpaRepository<ReferenceCardLayout, UUID> {

    Optional<ReferenceCardLayout> findTopByCodeAndActiveTrueOrderByVersionDesc(String code);
}
