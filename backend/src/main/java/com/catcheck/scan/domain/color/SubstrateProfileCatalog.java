package com.catcheck.scan.domain.color;

import java.util.Optional;

public interface SubstrateProfileCatalog {

    Optional<Lab> findReferenceLab(String productLine, String productionBatch);
}
