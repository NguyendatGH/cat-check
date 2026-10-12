package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatRepository {

    Optional<Cat> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Cat> findAllByOwnerId(UUID ownerId, CatStatus statusFilter, boolean includeArchived);

    long countActiveByOwnerId(UUID ownerId);

    Optional<Cat> findPrimaryByOwnerId(UUID ownerId);

    void clearPrimaryForOwner(UUID ownerId, Instant now);

    boolean existsByPublicCode(String publicCode);

    Cat save(Cat cat);
}
