package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.Cat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CatResponse(
        String id,
        String publicCode,
        String name,
        LocalDate birthDate,
        Integer approxAgeMonths,
        Integer ageMonths,
        String breedCode,
        String breedName,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        BigDecimal weightKg,
        Instant weightUpdatedAt,
        String avatarUrl,
        String status,
        boolean isPrimary,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant lastScanAt,
        String lastClassification,
        Long unacknowledgedFlagCount
) {

    public static CatResponse from(Cat cat, String breedName, String avatarUrl, LocalDate today,
                                   Instant lastScanAt, String lastClassification, Long unacknowledgedFlagCount) {
        return new CatResponse(
                cat.getId().toString(),
                cat.getPublicCode(),
                cat.getName(),
                cat.getBirthDate(),
                cat.getApproxAgeMonths(),
                cat.ageMonths(today),
                cat.getBreedCode(),
                breedName,
                cat.getBreedOther(),
                cat.getCoatColor(),
                cat.getSex().name(),
                cat.getNeutered(),
                cat.getWeightKg(),
                cat.getWeightUpdatedAt(),
                avatarUrl,
                cat.getStatus().name(),
                cat.isPrimary(),
                cat.getNotes(),
                cat.getCreatedAt(),
                cat.getUpdatedAt(),
                lastScanAt,
                lastClassification,
                unacknowledgedFlagCount);
    }
}
