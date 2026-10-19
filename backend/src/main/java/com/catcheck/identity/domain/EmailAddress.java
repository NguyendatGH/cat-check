package com.catcheck.identity.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;

    private static final Pattern SHAPE = Pattern.compile(
            "^[^\\s@]{1,64}@[^\\s@.]+(\\.[^\\s@.]+)+$");

    public EmailAddress {
        Objects.requireNonNull(value, "email khong duoc null");
        String normalized = normalize(value);
        if (normalized.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Email qua dai: toi da " + MAX_LENGTH + " ky tu");
        }
        if (!SHAPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Email khong hop le");
        }
        value = normalized;
    }

    public static EmailAddress of(String raw) {
        return new EmailAddress(raw);
    }

    public static EmailAddress parseOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new EmailAddress(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() {
        return value;
    }
}
