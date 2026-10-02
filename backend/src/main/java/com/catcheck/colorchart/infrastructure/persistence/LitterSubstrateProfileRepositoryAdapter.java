package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.LitterSubstrateProfile;
import com.catcheck.colorchart.domain.ProductLine;
import com.catcheck.colorchart.domain.port.LitterSubstrateProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Bộ chuyển đổi từ cổng {@link LitterSubstrateProfileRepository} sang Spring Data (R2).
 */
@Repository
class LitterSubstrateProfileRepositoryAdapter implements LitterSubstrateProfileRepository {

    private final LitterSubstrateProfileJpaRepository jpaRepository;

    LitterSubstrateProfileRepositoryAdapter(LitterSubstrateProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<LitterSubstrateProfile> findActiveByProductLineAndBatch(ProductLine productLine, String batchCode) {
        return jpaRepository.findTopByProductLineAndBatchCodeAndActiveTrueOrderByCreatedAtAsc(productLine, batchCode);
    }
}
