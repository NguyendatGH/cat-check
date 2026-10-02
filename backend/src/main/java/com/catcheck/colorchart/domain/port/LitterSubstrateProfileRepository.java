package com.catcheck.colorchart.domain.port;

import com.catcheck.colorchart.domain.LitterSubstrateProfile;
import com.catcheck.colorchart.domain.ProductLine;

import java.util.Optional;

/**
 * Cổng đọc {@code litter_substrate_profile}.
 */
public interface LitterSubstrateProfileRepository {

    Optional<LitterSubstrateProfile> findActiveByProductLineAndBatch(ProductLine productLine, String batchCode);
}
