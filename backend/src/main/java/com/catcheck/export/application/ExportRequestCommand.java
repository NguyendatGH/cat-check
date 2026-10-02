package com.catcheck.export.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Lệnh {@code POST /api/v1/exports} (p8 §8.4.10 J1, p4 G1). */
public record ExportRequestCommand(
        UUID userId,
        UUID catId,
        LocalDate rangeFrom,
        LocalDate rangeTo,
        String rangePreset,
        List<String> sections,
        String locale,
        String timezone
) {
}
