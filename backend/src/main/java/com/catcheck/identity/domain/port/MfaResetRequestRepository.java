package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaResetRequest;
import com.catcheck.identity.domain.MfaResetRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MfaResetRequestRepository {

    void insert(MfaResetRequest request);

    Optional<MfaResetRequest> findById(UUID id);

    Optional<MfaResetRequest> findPendingByTargetUserId(UUID targetUserId);

    List<MfaResetRequest> findPendingQueue();

    void markApproved(UUID id, UUID approvedBy, java.time.Instant approvedAt);

    void markRejected(UUID id, UUID approvedBy, java.time.Instant approvedAt, String rejectReason);

    void markCancelled(UUID id);

    void markExpired(UUID id);

    void markSubjectNotified(UUID id, java.time.Instant notifiedAt);

    int countRequestedBySince(UUID requestedBy, java.time.Instant since);
}
