package com.catcheck.cat.domain.port;

import com.catcheck.cat.domain.CatBreed;

import java.util.List;
import java.util.Optional;

public interface CatBreedRepository {

    List<CatBreed> findActive();

    Optional<CatBreed> findByCode(String code);
}
