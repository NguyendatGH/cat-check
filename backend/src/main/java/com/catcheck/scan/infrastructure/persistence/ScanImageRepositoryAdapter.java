package com.catcheck.scan.infrastructure.persistence;

import com.catcheck.scan.domain.ScanImage;
import com.catcheck.scan.domain.port.ScanImageRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class ScanImageRepositoryAdapter implements ScanImageRepository {

    private final ScanImageJpaRepository jpaRepository;

    ScanImageRepositoryAdapter(ScanImageJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ScanImage save(ScanImage image) {
        return jpaRepository.save(image);
    }

    @Override
    public Optional<ScanImage> findByScanId(UUID scanId) {
        return jpaRepository.findByScanId(scanId);
    }
}
