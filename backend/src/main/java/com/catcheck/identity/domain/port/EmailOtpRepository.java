package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.OtpPurpose;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailOtpRepository {

    Optional<EmailOtpChallenge> findActive(String email, OtpPurpose purpose);

    Optional<EmailOtpChallenge> findById(UUID challengeId);

    Optional<EmailOtpChallenge> findByTicketHash(String ticketHash);

    void insert(EmailOtpChallenge challenge);

    void supersedeActive(String email, OtpPurpose purpose, Instant consumedAt);

    int incrementAttempt(UUID challengeId);

    void markVerified(UUID challengeId, Instant verifiedAt, String ticketHash, Instant ticketExpiresAt);

    void markConsumed(UUID challengeId, Instant consumedAt);

    int deleteOlderThan(Instant createdBefore);
}
