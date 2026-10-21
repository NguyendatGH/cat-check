package com.catcheck.shared.error;

import java.net.URI;

/**
 * Gốc URI cho trường {@code type} của ProblemDetail, đọc từ env {@code CATCHECK_PROBLEM_BASE_URL}.
 * Không đặt env thì dùng URI tương đối {@code /problems/} (RFC 9457 cho phép), không gắn cứng domain.
 */
public final class ProblemTypes {

    private static final String BASE = normalize(System.getenv("CATCHECK_PROBLEM_BASE_URL"));

    private ProblemTypes() {
    }

    public static URI of(String slug) {
        return URI.create(BASE + slug);
    }

    static String normalize(String base) {
        if (base == null || base.isBlank()) {
            return "/problems/";
        }
        String trimmed = base.trim();
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
