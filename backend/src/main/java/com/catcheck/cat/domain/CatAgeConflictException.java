package com.catcheck.cat.domain;

/**
 * The age source is mutually exclusive: a cat has either a birth date or an approximate age.
 *
 * <p>The application boundary maps this domain violation to {@code CAT_AGE_CONFLICT}; keeping
 * this type separate from other invalid arguments avoids deriving an API error code from an
 * exception message.</p>
 */
public final class CatAgeConflictException extends IllegalArgumentException {

    public CatAgeConflictException() {
        super("Only one age source is allowed: birthDate or approxAgeMonths");
    }
}
