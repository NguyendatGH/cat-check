package com.catcheck.identity.domain;

public enum AppLocale {

    VI("vi"),
    EN("en");

    private final String code;

    AppLocale(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AppLocale fromCode(String value) {
        for (AppLocale candidate : values()) {
            if (candidate.code.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Locale khong ho tro: " + value);
    }
}
