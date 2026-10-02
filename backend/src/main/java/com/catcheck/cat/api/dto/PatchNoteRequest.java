package com.catcheck.cat.api.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** D18 — merge-patch ghi chú ({@code PATCH /cat-notes/{id}}). Trường {@code null} = giữ nguyên. */
public record PatchNoteRequest(
        String noteType,
        @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
        String body,
        LocalDate occurredOn
) {
}
