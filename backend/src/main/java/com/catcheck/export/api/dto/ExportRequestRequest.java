package com.catcheck.export.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

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
