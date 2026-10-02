package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatBreed;

/** F2 — {@code GET /reference/cat-breeds}. Nhãn đã resolve theo locale (không trả cả vi lẫn en). */
public record CatBreedResponse(String code, String name, boolean popular) {

    public static CatBreedResponse from(CatBreed breed, boolean english) {
        return new CatBreedResponse(
                breed.getCode(), english ? breed.getNameEn() : breed.getNameVi(), breed.isPopular());
    }
}
