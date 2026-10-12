package com.catcheck.notification.domain;

import java.util.UUID;

public record PushTarget(UUID subscriptionId, String installationId, String legacyToken) {

    public PushTarget {
        if (installationId == null && legacyToken == null) {
            throw new IllegalArgumentException("pushTarget phải có fid hoặc legacyToken");
        }
    }

    public boolean byInstallationId() {
        return installationId != null;
    }

    public String value() {
        return installationId != null ? installationId : legacyToken;
    }
}
