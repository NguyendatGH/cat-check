package com.catcheck.privacy.application.export;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface DsarExportJobPort {
    DsarExportJob enqueue(UUID requestId, String downloadTokenHash);
    Optional<DsarExportJob> findByRequestId(UUID requestId);
    boolean claim(UUID requestId);
    void complete(UUID requestId, String storageKey, Instant expiresAt);
    void fail(UUID requestId, String errorCode);
    boolean claimDownload(UUID requestId, String downloadTokenHash, Instant now);
}
