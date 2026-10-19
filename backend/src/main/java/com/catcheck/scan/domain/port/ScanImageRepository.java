package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanImage;

import java.util.Optional;
import java.util.UUID;

public interface ScanImageRepository {

    ScanImage save(ScanImage image);

    Optional<ScanImage> findByScanId(UUID scanId);
}
