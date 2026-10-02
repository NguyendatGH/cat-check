package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.ScanImage;

import java.util.Optional;
import java.util.UUID;

/** Cổng đọc/ghi {@code scan_image} (p4 D2). Tối đa một dòng mỗi {@code scan_id} (UNIQUE). */
public interface ScanImageRepository {

    ScanImage save(ScanImage image);

    Optional<ScanImage> findByScanId(UUID scanId);
}
