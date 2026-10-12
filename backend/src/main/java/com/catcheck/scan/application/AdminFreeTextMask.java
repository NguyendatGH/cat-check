package com.catcheck.scan.application;

public final class AdminFreeTextMask {

    private static final String MASKED = "***";

    private AdminFreeTextMask() {
    }

    public static String text(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        if (trimmed.length() <= 1) {
            return MASKED;
        }
        return trimmed.charAt(0) + MASKED;
    }
}
