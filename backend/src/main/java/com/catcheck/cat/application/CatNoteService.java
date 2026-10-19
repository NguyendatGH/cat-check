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

    @Transactional(readOnly = true)
    public NotePage listByCat(UUID ownerId, UUID catId, NoteType typeFilter, int page, int size) {
        requireOwnedCat(ownerId, catId);
        List<CatNote> items = catNoteRepository.findByCatId(catId, typeFilter, page, size);
        long total = catNoteRepository.countByCatId(catId, typeFilter);
        boolean hasMore = (long) (page + 1) * size < total;
        return new NotePage(items, hasMore);
    }

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

    @Transactional
    public CatNote patch(UUID ownerId, UUID noteId, NoteType noteType, String body, LocalDate occurredOn) {
        requireWriteAllowed(ownerId);
        CatNote note = requireEditableNote(ownerId, noteId);
        note.update(noteType, body, occurredOn, LocalDate.now(clock), clock.instant());
        return catNoteRepository.save(note);
    }

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
        catRepository.findByIdAndOwnerId(note.getCatId(), ownerId)
                .filter(cat -> !cat.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOTE_NOT_FOUND));
        if (!note.getAuthorUserId().equals(ownerId)) {
            throw new PermissionDeniedException(CatErrorCode.CAT_NOTE_NOT_EDITABLE);
        }
        return note;
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .filter(cat -> !cat.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    public record NotePage(List<CatNote> items, boolean hasMore) {
    }
}
