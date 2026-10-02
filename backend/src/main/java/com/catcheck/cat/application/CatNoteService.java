package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.NoteType;
import com.catcheck.cat.domain.port.CatNoteRepository;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ghi chú của chủ nuôi — D16 đến D19 của p8 §8.4.4.
 *
 * <p><b>D18/D19 không mang {@code catId} trên path</b> ({@code PATCH /cat-notes/{id}},
 * {@code DELETE /cat-notes/{id}}), nên xác nhận quyền sở hữu đi qua hai bước: nạp ghi chú bằng
 * {@code id} thuần rồi tra {@code CatRepository.findByIdAndOwnerId(note.getCatId(), ownerId)}.
 * Trả {@code 404 CAT_NOTE_NOT_FOUND} khi mèo không thuộc người gọi — KHÔNG trả 403 — cùng lý do
 * IDOR nêu ở {@code CatErrorCode} (không xác nhận id đó có tồn tại).</p>
 */
@Service
public class CatNoteService {

    private final CatNoteRepository catNoteRepository;
    private final CatRepository catRepository;
    private final UserAccountPort userAccountPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public CatNoteService(
            CatNoteRepository catNoteRepository,
            CatRepository catRepository,
            UserAccountPort userAccountPort,
            UuidV7 uuidV7,
            Clock clock) {
        this.catNoteRepository = catNoteRepository;
        this.catRepository = catRepository;
        this.userAccountPort = userAccountPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    /** D16 — nhật ký ghi chú của một mèo, mới nhất trước. */
    @Transactional(readOnly = true)
    public NotePage listByCat(UUID ownerId, UUID catId, NoteType typeFilter, int page, int size) {
        requireOwnedCat(ownerId, catId);
        List<CatNote> items = catNoteRepository.findByCatId(catId, typeFilter, page, size);
        long total = catNoteRepository.countByCatId(catId, typeFilter);
        boolean hasMore = (long) (page + 1) * size < total;
        return new NotePage(items, hasMore);
    }

    /**
     * D17 — thêm ghi chú.
     *
     * <p>Chặn ghi khi mèo đã {@code ARCHIVED} ({@code 409 CAT_ARCHIVED}, đúng bảng lỗi p8 "Các
     * endpoint còn lại của nhóm D") — mèo đã lưu trữ vẫn xem được lịch sử nhưng không nhận thêm
     * dữ liệu mới (p4 §4.8.2).</p>
     */
    @Transactional
    public CatNote create(UUID ownerId, UUID catId, NoteType noteType, String body, LocalDate occurredOn) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        if (cat.isArchived()) {
            throw new ConflictException(CatErrorCode.CAT_ARCHIVED, catId);
        }
        Instant now = clock.instant();
        CatNote note = CatNote.create(
                uuidV7.generate(), catId, ownerId, noteType, body, occurredOn, LocalDate.now(clock), now);
        return catNoteRepository.save(note);
    }

    /** D18 — sửa ghi chú của chính mình (merge-patch). */
    @Transactional
    public CatNote patch(UUID ownerId, UUID noteId, NoteType noteType, String body, LocalDate occurredOn) {
        requireWriteAllowed(ownerId);
        CatNote note = requireEditableNote(ownerId, noteId);
        note.update(noteType, body, occurredOn, LocalDate.now(clock), clock.instant());
        return catNoteRepository.save(note);
    }

    /** D19 — xoá mềm ghi chú của chính mình. */
    @Transactional
    public void softDelete(UUID ownerId, UUID noteId) {
        requireWriteAllowed(ownerId);
        CatNote note = requireEditableNote(ownerId, noteId);
        note.softDelete(clock.instant());
        catNoteRepository.save(note);
    }

    private CatNote requireEditableNote(UUID ownerId, UUID noteId) {
        CatNote note = catNoteRepository.findById(noteId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOTE_NOT_FOUND));
        // Mèo phải thuộc người gọi — 404 (không phải 403) nếu không, để không xác nhận id có thật.
        catRepository.findByIdAndOwnerId(note.getCatId(), ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOTE_NOT_FOUND));
        if (!note.getAuthorUserId().equals(ownerId)) {
            throw new PermissionDeniedException(CatErrorCode.CAT_NOTE_NOT_EDITABLE);
        }
        return note;
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    /** Trang kết quả D16 — {@code items} + cờ còn dữ liệu hay không. */
    public record NotePage(List<CatNote> items, boolean hasMore) {
    }
}
