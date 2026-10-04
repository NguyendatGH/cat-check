package com.catcheck.privacy.application.export;

import java.time.Instant;
import java.util.UUID;

public record DsarExportJob(UUID id, UUID requestId, String status, int attempts,
                            String storageKey, Instant expiresAt, Instant downloadedAt) { }
