package com.catcheck.scan.domain.port;

import java.util.Optional;
import java.util.UUID;

public interface CatOwnershipPort {

    Optional<CatSnapshot> findSnapshot(UUID catId);

    record CatSnapshot(UUID catId, UUID ownerId, String name, boolean archived, boolean deleted) {

        public boolean isOwnedBy(UUID userId) {
            return !deleted && ownerId.equals(userId);
        }
    }
}
