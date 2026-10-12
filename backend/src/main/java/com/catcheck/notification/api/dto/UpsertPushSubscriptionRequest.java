package com.catcheck.notification.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpsertPushSubscriptionRequest(
        @Size(max = 255) String fid,
        @Size(max = 512) String legacyToken,
        @Pattern(regexp = "WEB|ANDROID_PWA|IOS_PWA") String platform,
        @Size(max = 100) String deviceLabel) {
}
