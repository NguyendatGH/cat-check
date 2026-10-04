package com.catcheck.notification.domain;

/** {@code push_subscription.platform} (p4 F3). iOS có giới hạn riêng — p12 §12.3.8. */
public enum PushPlatform {
    WEB,
    ANDROID_PWA,
    IOS_PWA
}
