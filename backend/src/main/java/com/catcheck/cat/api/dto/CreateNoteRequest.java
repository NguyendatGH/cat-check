package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateNoteRequest(
        String noteType,
        @NotBlank(message = "{validation.cat.note.required}")
        @Size(max = 2000, message = "{validation.cat.note.max}")
        String body,
        LocalDate occurredOn
) {
}
