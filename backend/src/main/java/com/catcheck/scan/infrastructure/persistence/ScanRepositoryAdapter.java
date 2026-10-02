package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.Scan;
import com.catcheck.scan.domain.port.ScanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Bộ chuyển đổi từ cổng {@link ScanRepository} sang Spring Data (R2). */
@Repository
class ScanRepositoryAdapter implements ScanRepository {

    private final ScanJpaRepository jpaRepository;

    ScanRepositoryAdapter(ScanJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Scan save(Scan scan) {
        return jpaRepository.save(scan);
    }

    @Override
    public Optional<Scan> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jpaRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
    }
}
