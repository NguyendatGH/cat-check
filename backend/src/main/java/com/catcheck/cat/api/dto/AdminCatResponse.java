package com.catcheck.cat.api.dto;

import com.catcheck.cat.application.AdminFreeTextMask;
import com.catcheck.cat.domain.Cat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record AdminCatResponse(
        String catId,
        String publicCode,
        String name,
        String status,
        boolean primary,
        LocalDate birthDate,
        Integer approxAgeMonths,
        String breedCode,
        String breedOther,
        String coatColor,
        String sex,
        Boolean neutered,
        BigDecimal weightKg,
        Instant weightUpdatedAt,
        String notes,
        boolean hasAvatar,
        Instant createdAt,
        Instant updatedAt
) {

    public static AdminCatResponse from(Cat cat, boolean fullData) {
        return new AdminCatResponse(
                cat.getId().toString(),
                cat.getPublicCode(),
                fullData ? cat.getName() : AdminFreeTextMask.text(cat.getName()),
                cat.getStatus() == null ? null : cat.getStatus().name(),
                cat.isPrimary(),
                cat.getBirthDate(),
                cat.getApproxAgeMonths(),
                cat.getBreedCode(),
                fullData ? cat.getBreedOther() : AdminFreeTextMask.text(cat.getBreedOther()),
                cat.getCoatColor(),
                cat.getSex() == null ? null : cat.getSex().name(),
                cat.getNeutered(),
                cat.getWeightKg(),
                cat.getWeightUpdatedAt(),
                fullData ? cat.getNotes() : AdminFreeTextMask.text(cat.getNotes()),
                cat.getAvatarStorageKey() != null,
                cat.getCreatedAt(),
                cat.getUpdatedAt());
    }
}
