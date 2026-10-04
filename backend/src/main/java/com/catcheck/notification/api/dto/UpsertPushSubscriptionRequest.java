package com.catcheck.notification.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code PUT /api/v1/push/subscriptions} (p8 §8.4.7 G10) — upsert theo {@code fid} (hoặc
 * {@code legacyToken}).
 *
 * <p>Thiếu CẢ hai ⇒ {@code 400 PUSH_SUBSCRIPTION_INVALID}; kiểm ở service chứ không bằng
 * {@code @NotBlank} trên từng field, vì điều kiện là "ít nhất một trong hai" chứ không phải
 * "cả hai bắt buộc".</p>
 */
public record UpsertPushSubscriptionRequest(
        @Size(max = 255) String fid,
        @Size(max = 512) String legacyToken,
        @Pattern(regexp = "WEB|ANDROID_PWA|IOS_PWA") String platform,
        @Size(max = 100) String deviceLabel) {
}
