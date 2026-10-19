package com.catcheck.scan.domain.port;

import com.catcheck.scan.domain.Scan;

import java.util.Optional;
import java.util.UUID;

public interface ScanRepository {

    Scan save(Scan scan);

    Optional<Scan> findById(UUID id);

    Optional<Scan> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    void flushPendingWrites();
}
