package com.catcheck.identity.api.dto;

/** B2 — PATCH hồ sơ, ngữ nghĩa merge-patch: trường {@code null} = không đổi (p8 §8.4.4 nhóm B). */
public record UpdateProfileRequest(
        String fullName,
        String phone,
        String locale,
        String timezone) {
}
