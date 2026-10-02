package com.catcheck.cat.api;

import java.time.Instant;
import java.util.UUID;

/**
 * D5 — hồ sơ mèo bị xoá mềm ({@code deleted_at} có giá trị).
 *
 * <p>Tách khỏi {@link CatUpdatedEvent} vì đây là thao tác không thể hoàn tác qua API: mọi job nền
 * đang giữ tham chiếu tới mèo này cần dừng lại ngay, và việc "xoá mềm" có thể bị hoàn nguyên khi
 * người dùng đổi ý trong 30 ngày nên không được coi là sự kiện thường.</p>
 *
 * <p>Sự kiện "gỡ khỏi theo dõi" (D6, archive) là {@link CatUpdatedEvent} vì nó hoàn tác được.</p>
 *
 * @param catId      hồ sơ bị xoá mềm
 * @param ownerId    chủ sở hữu
 * @param softDelete luôn {@code true} — có để tương lai dùng cho xoá cứng sau khi hết thời hạn khôi
 *                   phục
 * @param occurredAt thời điểm phát
 */
public record CatArchivedEvent(
        UUID catId,
        UUID ownerId,
        boolean softDelete,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_DELETED";
    }
}
