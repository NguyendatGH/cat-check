package com.catcheck.export.application;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
