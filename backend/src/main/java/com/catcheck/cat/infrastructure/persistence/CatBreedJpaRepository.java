package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatBreed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Spring Data repository cho {@code cat_breed} — khoá tự nhiên {@code code} (p4 C2). */
interface CatBreedJpaRepository extends JpaRepository<CatBreed, String> {

    List<CatBreed> findByActiveTrueOrderBySortOrderAsc();
}
