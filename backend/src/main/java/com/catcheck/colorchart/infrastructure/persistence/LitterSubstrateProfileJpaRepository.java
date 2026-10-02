package com.catcheck.colorchart.infrastructure.persistence;

import com.catcheck.colorchart.domain.LitterSubstrateProfile;
import com.catcheck.colorchart.domain.ProductLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository cho {@code litter_substrate_profile} (R7).
 */
interface LitterSubstrateProfileJpaRepository extends JpaRepository<LitterSubstrateProfile, java.util.UUID> {

    Optional<LitterSubstrateProfile> findTopByProductLineAndBatchCodeAndActiveTrueOrderByCreatedAtAsc(
            ProductLine productLine, String batchCode);
}
