package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatBreed;
import com.catcheck.cat.domain.port.CatBreedRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class CatBreedRepositoryAdapter implements CatBreedRepository {

    private final CatBreedJpaRepository jpaRepository;

    CatBreedRepositoryAdapter(CatBreedJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<CatBreed> findActive() {
        return jpaRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @Override
    public Optional<CatBreed> findByCode(String code) {
        return jpaRepository.findById(code);
    }
}
