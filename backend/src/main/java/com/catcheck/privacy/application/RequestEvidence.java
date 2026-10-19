package com.catcheck.privacy.application;

import java.util.UUID;

public record RequestEvidence(
        String requestId,
        String ipAddress,
        String userAgent
) {

    public static RequestEvidence of(String requestId, String ipAddress, String userAgent) {
        return new RequestEvidence(
                requestId != null && requestId.length() > 64 ? requestId.substring(0, 64) : requestId,
                ipAddress,
                userAgent);
    }

    public static RequestEvidence system() {
        return new RequestEvidence(null, null, null);
    }
}
