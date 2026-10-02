package com.catcheck.identity.api.dto;

import java.util.List;

/** A6 — thách thức MFA bước 2 khi đăng nhập (p8 §8.4.4 nhóm A). */
public record MfaChallengeResponse(
        boolean mfaRequired,
        List<String> mfaMethods) {
}
