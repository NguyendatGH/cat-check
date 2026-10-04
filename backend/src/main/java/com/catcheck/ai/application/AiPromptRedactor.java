package com.catcheck.ai.application;

import java.util.regex.Pattern;

/** Redacts common contact PII before conversation text leaves CatCheck for an AI provider. */
final class AiPromptRedactor {
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)(?<![A-Z0-9._%+-])[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}(?![A-Z0-9.-])");
    private static final Pattern VIETNAMESE_PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+?84|0)(?:[\\s().-]*\\d){8,10}(?!\\d)");

    private AiPromptRedactor() { }

    static String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        return VIETNAMESE_PHONE.matcher(EMAIL.matcher(text).replaceAll("[đã ẩn email]"))
                .replaceAll("[đã ẩn số điện thoại]");
    }
}
