package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.OtpPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Cong vao {@code email_otp}. Trien khai o {@code ..infrastructure.persistence} (R7).
 */
public interface EmailOtpRepository {

    /**
     * Challenge ACTIVE moi nhat cho {@code (email, purpose)}. Chi lay chua
     * {@code consumed_at} vi unique index cuc bo {@code uq_email_otp_active_per_purpose}
     * cung cap bat bien "mot challenge ACTIVE" o cap DB.
     */
    Optional<EmailOtpChallenge> findActive(String email, OtpPurpose purpose);

    Optional<EmailOtpChallenge> findById(UUID challengeId);

    /**
     * Challenge da xac thuc va con ticket con hieu luc — dung khi {@code POST /auth/register}
     * hoac {@code /auth/password-reset/confirm} cham ticket.
     */
    Optional<EmailOtpChallenge> findByTicketHash(String ticketHash);

    /**
     * Chen challenge moi, dong challenge dang ACTIVE truoc do trong cung lenh
     * (p11 §11.2.1 "sinh ma moi vo hieu ma cu").
     */
    void insert(EmailOtpChallenge challenge);

    void supersedeActive(String email, OtpPurpose purpose, Instant consumedAt);

    /** Cong so lan nhap sai. Tra ve so moi sau khi tang. */
    int incrementAttempt(UUID challengeId);

    void markVerified(UUID challengeId, Instant verifiedAt, String ticketHash, Instant ticketExpiresAt);

    /** Danh dau het luot sau khi dung (xac thanh, het han, het so lan thu). */
    void markConsumed(UUID challengeId, Instant consumedAt);

    int deleteOlderThan(Instant createdBefore);
}
