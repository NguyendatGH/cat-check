package com.catcheck.identity.api.dto;

import java.util.List;

public record MfaChallengeResponse(
        boolean mfaRequired,
        List<String> mfaMethods) {
}
