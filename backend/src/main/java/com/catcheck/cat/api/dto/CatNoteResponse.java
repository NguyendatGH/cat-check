package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatNote;

import java.time.Instant;
import java.time.LocalDate;

/**
 * D16-D19 — trả về một ghi chú.
 *
 * <p>{@code scanId} luôn {@code null} ở M2: cột {@code cat_note.scan_id} chỉ tồn tại từ V11 (module
 * {@code scan}, đang làm song song — xem {@code V8__cat.sql}). FE (`entities/cat/model.ts`) đã có
 * sẵn trường này trong kiểu {@code CatNote} nên trả {@code null} thay vì bỏ hẳn trường, để không
 * phải sửa lại type phía FE khi {@code scan} bổ sung cột.</p>
 */
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
