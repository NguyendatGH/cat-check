package com.catcheck.cat.api.dto;

import com.catcheck.cat.domain.CatBreed;

public record CatBreedResponse(String code, String name, boolean popular) {

    public static CatBreedResponse from(CatBreed breed, boolean english) {
        return new CatBreedResponse(
                breed.getCode(), english ? breed.getNameEn() : breed.getNameVi(), breed.isPopular());
    }
}
