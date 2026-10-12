package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.ConsentRecord;
import com.catcheck.privacy.domain.ConsentStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordPort {

    void append(ConsentRecord record);

    Optional<ConsentRecord> findLatest(UUID userId, String purposeCode);

    ConsentStatus findCurrentStatus(UUID userId, String purposeCode);

    default List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, int limit) {
        return findHistoryByUser(userId, before, null, limit);
    }

    List<ConsentRecord> findHistoryByUser(UUID userId, Instant before, UUID beforeId, int limit);

    Optional<ConsentRecord> findById(UUID id);
}
