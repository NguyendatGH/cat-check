package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaRecoveryCode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MfaRecoveryCodeRepository {

    void insertBatch(UUID userId, List<MfaRecoveryCode> codes);

    List<MfaRecoveryCode> findUnusedByUserId(UUID userId);

    boolean markUsed(UUID codeId, UUID userId, Instant usedAt, String usedIp);

    long countUnusedByUserId(UUID userId);

    int deleteByUserId(UUID userId);
}
