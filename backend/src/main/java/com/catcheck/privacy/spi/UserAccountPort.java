package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.UUID;

public interface UserAccountPort {

    UserAccountSnapshot snapshot(UUID userId);

    void requestAccountDeletion(UUID userId, Instant scheduledAt);

    void cancelAccountDeletion(UUID userId);

    void setProcessingRestricted(UUID userId);

    void liftProcessingRestriction(UUID userId);
}
