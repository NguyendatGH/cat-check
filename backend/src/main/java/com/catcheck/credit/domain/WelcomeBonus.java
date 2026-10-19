package com.catcheck.credit.domain;

import java.util.UUID;

/**
 * Quà chào mừng: một lô credit tặng đúng MỘT lần cho mỗi tài khoản người mua mới.
 *
 * <p>Lô quà dùng {@code credit_batch.package_code = 'WELCOME'} — cột này là snapshot không FK tới
 * {@code package_plan} (V10), nên không cần migration. Lô quà KHÔNG được coi là "gói đã kích hoạt"
 * khi tính {@code user_entitlement.highest_package}: nó chỉ cho lượt quét, không mở tính năng gói.
 */
public final class WelcomeBonus {

    public static final String PACKAGE_CODE = "WELCOME";

    public static final int PACKAGE_VERSION = 1;

    public static final String LEDGER_NOTE = "Quà chào mừng";

    private static final String IDEMPOTENCY_PREFIX = "welcome-bonus:";

    private WelcomeBonus() {
    }

    /** Khoá UNIQUE trên credit_ledger.idempotency_key — chốt "một user một lần" ở mức DB. */
    public static String idempotencyKey(UUID userId) {
        return IDEMPOTENCY_PREFIX + userId;
    }

    public static boolean isWelcomePackage(String packageCode) {
        return PACKAGE_CODE.equals(packageCode);
    }
}
