package com.catcheck.export.domain.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface SubjectSnapshotPort {

    Optional<CatProfile> findCatProfile(UUID catId);

    Optional<OwnerProfile> findOwnerProfile(UUID userId);

    record CatProfile(
            UUID catId,
            UUID ownerId,
            String name,
            String breedLabel,
            String sex,
            LocalDate birthDate,
            Integer approxAgeMonths,
            BigDecimal weightKg,
            String avatarStorageKey
    ) {
    }

    record OwnerProfile(UUID userId, String fullName, String locale, String timezone) {
    }
}
