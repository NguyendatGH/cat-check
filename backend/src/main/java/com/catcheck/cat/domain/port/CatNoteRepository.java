package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.NoteType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cổng đọc/ghi bảng {@code cat_note} (D14 đến D17, p4 §4.4.4).
 */
public interface CatNoteRepository {

    Optional<CatNote> findByIdAndCatId(UUID id, UUID catId);

    /**
     * Tra theo id thuần, KHÔNG kèm {@code catId}.
     *
     * <p>Bổ sung cho D18/D19 (p8 §8.4.4): {@code PATCH /cat-notes/{id}} và
     * {@code DELETE /cat-notes/{id}} không mang {@code catId} trên path, nên tầng application
     * phải tự nạp ghi chú trước rồi mới xác nhận mèo liên quan thuộc về người gọi (qua
     * {@code CatRepository.findByIdAndOwnerId(note.getCatId(), ownerId)}). Thêm method này thay vì
     * đổi chữ ký {@link #findByIdAndCatId(UUID, UUID)} vì method cũ vẫn cần cho các luồng đã biết
     * {@code catId} từ trước.</p>
     */
    Optional<CatNote> findById(UUID id);

    /** D14 — ghi chú còn hiệu lực của một mèo, mới nhất trước, lọc theo loại nếu có. */
    List<CatNote> findByCatId(UUID catId, NoteType typeFilter, int page, int size);

    long countByCatId(UUID catId, NoteType typeFilter);

    CatNote save(CatNote note);

    /** D16 — xoá mềm hàng loạt, dùng cho DSAR. */
    void softDeleteAllByCatId(UUID catId, Instant now);
}
