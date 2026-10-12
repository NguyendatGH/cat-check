package com.catcheck.cat.infrastructure.persistence;

import com.catcheck.cat.domain.CatBreed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface CatBreedJpaRepository extends JpaRepository<CatBreed, String> {

    List<CatBreed> findByActiveTrueOrderBySortOrderAsc();
}
