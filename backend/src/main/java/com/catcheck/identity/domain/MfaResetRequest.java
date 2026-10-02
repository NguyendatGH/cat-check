package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup {@code user_mfa_reset_request} (p4 §A9) — hang doi yeu cau reset TOTP
 * theo quy tac hai nguoi (p11 §11.12.3).
 *
 * <p>Bang nay KHONG phai append-only (trang thai phai chuyen duoc), nhung
 * {@code reason}, {@code requestedBy}, {@code requestedAt} khong duoc UPDATE sau khi
 * tao — ep o service, bang chung ben nam o {@code audit_log}.</p>
 */
public record MfaResetRequest(
        UUID id,
        UUID targetUserId,
        UUID requestedBy,
        Instant requestedAt,
        String reason,
        UUID approvedBy,
        Instant approvedAt,
        MfaResetRequestStatus status,
        String rejectReason,
        Instant expiresAt,
        Instant subjectNotifiedAt,
        Instant createdAt,
        Instant updatedAt) {
}
