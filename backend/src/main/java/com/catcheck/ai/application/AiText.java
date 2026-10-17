package com.catcheck.ai.application;

import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Chuẩn hoá câu hỏi tiếng Việt cho retrieval. {@link #fold} phải khớp hàm SQL {@code ai_fold}
 * (unaccent + lower, V34) để câu gõ không dấu và có dấu cho cùng một bộ từ.
 */
public final class AiText {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern TOKEN = Pattern.compile("[a-z0-9]+");
    private static final int MAX_TERMS = 12;

    // Từ chức năng (đã bỏ dấu) không mang nội dung tìm kiếm.
    private static final Set<String> STOPWORDS = Set.of(
            "la", "gi", "cua", "toi", "ban", "minh", "em", "anh", "chi", "cho", "va", "co", "khong", "the",
            "duoc", "thi", "nen", "lam", "sao", "bao", "nhieu", "lau", "nhung", "cac", "mot", "nay", "do",
            "kia", "da", "bi", "voi", "trong", "khi", "de", "hay", "hoac", "ma", "nhu", "o", "ve", "nao",
            "hom", "a", "oi", "nhe", "vay", "roi", "can", "phai", "muon", "se", "dang", "ai", "dau");

    private AiText() {
    }

    public static String fold(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD);
        return MARKS.matcher(decomposed).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D')
                .toLowerCase(Locale.ROOT);
    }

    /** Từ nội dung (đã bỏ dấu, bỏ trùng, giữ thứ tự), chỉ gồm [a-z0-9] nên an toàn khi ghép vào tsquery. */
    public static List<String> contentTerms(String text) {
        Set<String> terms = new LinkedHashSet<>();
        var matcher = TOKEN.matcher(fold(text));
        while (matcher.find() && terms.size() < MAX_TERMS) {
            String term = matcher.group();
            if (!STOPWORDS.contains(term)) {
                terms.add(term);
            }
        }
        return List.copyOf(terms);
    }
}
