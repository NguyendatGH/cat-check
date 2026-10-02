package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.UserActivityLog;

import java.util.UUID;

/**
 * Cổng ghi {@code user_activity_log} (p4 B11). Ghi CHỈ khi user đồng ý
 * {@code PRODUCT_ANALYTICS} và tài khoản không RESTRICTED (p15 REQ-PRIV-02) — kiểm tra
 * nằm ở tầng service gọi, không phải ở đây.
 */
public interface UserActivityLogPort {

    /** INSERT một sự kiện — {@code props} không chứa PII, không chứa giá trị pH (I29). */
    void append(UserActivityLog event);

    /** Xoá cứng toàn bộ log hành vi của user — bắt buộc khi xoá tài khoản (p4 B11, p15 §15.4.6). */
    int deleteByUser(UUID userId);
}
