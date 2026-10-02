package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.ReferenceCardLayout;
import com.catcheck.colorchart.domain.port.ReferenceCardLayoutRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link ReferenceCardLayoutRepository} sang Spring Data (R2).
 */
@Repository
class ReferenceCardLayoutRepositoryAdapter implements ReferenceCardLayoutRepository {

    private final ReferenceCardLayoutJpaRepository jpaRepository;

    ReferenceCardLayoutRepositoryAdapter(ReferenceCardLayoutJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<ReferenceCardLayout> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<ReferenceCardLayout> findActiveByCode(String code) {
        return jpaRepository.findTopByCodeAndActiveTrueOrderByVersionDesc(code);
    }
}
