package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record PatchNoteRequest(
        String noteType,
        @Size(max = 2000, message = "{validation.cat.note.max}")
        String body,
        LocalDate occurredOn
) {
}
