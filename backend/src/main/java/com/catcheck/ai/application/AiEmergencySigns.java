package com.catcheck.ai.application;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Nhận diện câu hỏi nhắc tới dấu hiệu khẩn cấp, để {@link AiChatService} rẽ sang nhánh khẩn cấp
 * kể cả khi mèo chưa có cờ {@code URGENT}.
 *
 * <p>Danh sách cụm từ CỐ ĐỊNH, chỉ lấy từ các dấu hiệu trong copy đã duyệt {@code ai.emergency.body}
 * ({@code messages/ai_vi.properties}). Không thêm dấu hiệu nào ngoài copy đó.</p>
 *
 * <p>So khớp theo cả cụm từ (biên từ) sau khi chuẩn hoá: chữ thường, gộp khoảng trắng/dấu câu, và
 * bỏ dấu tiếng Việt. Riêng vài cụm khi bỏ dấu sẽ trùng chữ thường gặp ({@code nôn}/"mèo non",
 * {@code máu}/"có màu vàng", {@code rặn}/"cá rán") thì chỉ khớp khi CÒN dấu, để câu hỏi về mèo
 * con hay màu nước tiểu không bị trả nhánh khẩn cấp.</p>
 */
final class AiEmergencySigns {

    private static final Pattern NON_WORD = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

    /** So khớp sau khi bỏ dấu. */
    private static final List<String> UNACCENTED_PHRASES = List.of(
            "khong ra nuoc tieu",
            "chi ra vai giot",
            "dau khi di ve sinh",
            "ra vao khay lien tuc",
            "khay van kho",
            "bo an",
            "li bi",
            "bung cang cung");

    /** So khớp khi còn dấu: bỏ dấu sẽ trùng chữ khác. */
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

    /** Chữ thường, mọi chuỗi ký tự không phải chữ/số thành một khoảng trắng, có khoảng trắng hai đầu. */
    private static String words(String text) {
        return " " + NON_WORD.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").strip() + " ";
    }
}
