package com.catcheck.cat.domain;

public final class CatAgeConflictException extends IllegalArgumentException {

    public CatAgeConflictException() {
        super("Only one age source is allowed: birthDate or approxAgeMonths");
    }
}
