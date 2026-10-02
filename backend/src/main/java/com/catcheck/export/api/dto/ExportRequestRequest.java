package com.catcheck.export.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** {@code POST /api/v1/exports} (p8 §8.4.10 J1). */
public record ExportRequestRequest(
        @NotNull String catId,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        String rangePreset,
        List<String> sections,
        String locale,
        String timezone
) {
}
