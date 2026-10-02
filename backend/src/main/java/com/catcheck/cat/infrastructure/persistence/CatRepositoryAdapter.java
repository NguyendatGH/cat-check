package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;
import com.catcheck.cat.domain.port.CatRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bộ chuyển đổi từ cổng {@link CatRepository} sang Spring Data — cùng khuôn với
 * {@code content.infrastructure.persistence.CareTipRepositoryAdapter}.
 */
@Repository
class CatRepositoryAdapter implements CatRepository {

    private final CatJpaRepository jpaRepository;

    CatRepositoryAdapter(CatJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId) {
        return jpaRepository.findByIdAndOwnerId(id, ownerId);
    }

    @Override
    public List<Cat> findAllByOwnerId(UUID ownerId, CatStatus statusFilter, boolean includeArchived) {
        return jpaRepository.findAllByOwnerId(ownerId, statusFilter, includeArchived);
    }

    @Override
    public long countActiveByOwnerId(UUID ownerId) {
        return jpaRepository.countByOwnerIdAndStatusAndDeletedAtIsNull(ownerId, CatStatus.ACTIVE);
    }

    @Override
    public Optional<Cat> findPrimaryByOwnerId(UUID ownerId) {
        return jpaRepository.findByOwnerIdAndPrimaryTrueAndDeletedAtIsNull(ownerId);
    }

    @Override
    public void clearPrimaryForOwner(UUID ownerId, Instant now) {
        jpaRepository.clearPrimaryForOwner(ownerId, now);
    }

    @Override
    public boolean existsByPublicCode(String publicCode) {
        return jpaRepository.existsByPublicCode(publicCode);
    }

    @Override
    public Cat save(Cat cat) {
        return jpaRepository.save(cat);
    }
}
