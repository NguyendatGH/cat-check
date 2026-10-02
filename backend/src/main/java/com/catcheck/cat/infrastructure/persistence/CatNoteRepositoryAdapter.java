package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatNote;
import com.catcheck.cat.domain.NoteType;
import com.catcheck.cat.domain.port.CatNoteRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CatNoteRepositoryAdapter implements CatNoteRepository {

    private final CatNoteJpaRepository jpaRepository;

    CatNoteRepositoryAdapter(CatNoteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CatNote> findByIdAndCatId(UUID id, UUID catId) {
        return jpaRepository.findByIdAndCatId(id, catId);
    }

    @Override
    public Optional<CatNote> findById(UUID id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<CatNote> findByCatId(UUID catId, NoteType typeFilter, int page, int size) {
        return jpaRepository.findByCatId(catId, typeFilter, PageRequest.of(Math.max(page, 0), Math.max(size, 1)));
    }

    @Override
    public long countByCatId(UUID catId, NoteType typeFilter) {
        return jpaRepository.countByCatId(catId, typeFilter);
    }

    @Override
    public CatNote save(CatNote note) {
        return jpaRepository.save(note);
    }

    @Override
    public void softDeleteAllByCatId(UUID catId, Instant now) {
        jpaRepository.softDeleteAllByCatId(catId, now);
    }
}
