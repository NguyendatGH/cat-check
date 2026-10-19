package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatNote;

import java.time.Instant;
import java.time.LocalDate;

public record CatNoteResponse(
        String id,
        String catId,
        String noteType,
        String body,
        LocalDate occurredOn,
        String scanId,
        Instant createdAt,
        Instant updatedAt
) {

    public static CatNoteResponse from(CatNote note) {
        return new CatNoteResponse(
                note.getId().toString(),
                note.getCatId().toString(),
                note.getNoteType().name(),
                note.getBody(),
                note.getOccurredOn(),
                null,
                note.getCreatedAt(),
                note.getUpdatedAt());
    }
}
