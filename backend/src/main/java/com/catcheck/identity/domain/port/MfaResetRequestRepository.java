package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.MfaResetRequest;
import com.catcheck.identity.domain.MfaResetRequestStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong vao {@code user_mfa_reset_request}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface MfaResetRequestRepository {

    void insert(MfaResetRequest request);

    Optional<MfaResetRequest> findById(UUID id);

    /** Yeu cau {@code PENDING} dang mo cua mot tai khoan — toi da 1 (unique index cuc bo). */
    Optional<MfaResetRequest> findPendingByTargetUserId(UUID targetUserId);

    List<MfaResetRequest> findPendingQueue();

    void markApproved(UUID id, UUID approvedBy, java.time.Instant approvedAt);

    void markRejected(UUID id, UUID approvedBy, java.time.Instant approvedAt, String rejectReason);

    void markCancelled(UUID id);

    void markExpired(UUID id);

    void markSubjectNotified(UUID id, java.time.Instant notifiedAt);

    /** Dem so lan mot admin thao tac trong 24h — tran 3 lan/ngay (p4 §A9). */
    int countRequestedBySince(UUID requestedBy, java.time.Instant since);
}
