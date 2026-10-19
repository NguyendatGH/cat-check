package com.catcheck.reminder.application.spi;

import java.util.Optional;
import java.util.UUID;

public interface CatOwnershipPort {

    boolean isOwnedAndAlive(UUID catId, UUID ownerId);

    Optional<String> findAliveCatName(UUID catId, UUID ownerId);
}
