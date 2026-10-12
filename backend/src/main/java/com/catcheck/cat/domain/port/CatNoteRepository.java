package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.NoteType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatNoteRepository {

    Optional<CatNote> findByIdAndCatId(UUID id, UUID catId);

    Optional<CatNote> findById(UUID id);

    List<CatNote> findByCatId(UUID catId, NoteType typeFilter, int page, int size);

    long countByCatId(UUID catId, NoteType typeFilter);

    CatNote save(CatNote note);

    void softDeleteAllByCatId(UUID catId, Instant now);
}
