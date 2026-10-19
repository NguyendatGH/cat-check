package com.catcheck.ai.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class AiEmergencySigns {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    private static final List<String> UNACCENTED_PHRASES = List.of(
            "khong ra nuoc tieu",
            "chi ra vai giot",
            "dau khi di ve sinh",
            "ra vao khay lien tuc",
            "khay van kho",
            "bo an",
            "li bi",
            "bung cang cung");

    private static final List<String> ACCENTED_PHRASES = List.of(
            "rặn",
            "nước tiểu có máu",
            "nôn");

    private AiEmergencySigns() {
    }

    static boolean mentionsEmergencySign(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String accented = words(Normalizer.normalize(text, Normalizer.Form.NFC));
        String unaccented = words(COMBINING_MARKS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD))
                .replaceAll("").replace('đ', 'd').replace('Đ', 'D'));
        return UNACCENTED_PHRASES.stream().anyMatch(phrase -> unaccented.contains(" " + phrase + " "))
                || ACCENTED_PHRASES.stream().anyMatch(phrase -> accented.contains(" " + phrase + " "));
    }

    private static String words(String text) {
        return " " + NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip() + " ";
    }
}
