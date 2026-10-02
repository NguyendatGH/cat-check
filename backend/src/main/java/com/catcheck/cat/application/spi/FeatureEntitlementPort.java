package com.catcheck.cat.application.spi;

import java.util.UUID;

/**
 * Cong kiem tra mot tinh nang co nam trong goi hien tai khong (p5 R5).
 *
 * <p>D13 {@code GET /cats/{catId}/trends} yeu cau {@code E:trend} (p8 §8.4.4 dong D13); thieu
 * thi tra {@code 403 FEATURE_NOT_IN_PLAN} (p8 §8.2.4 dong 480).</p>
 *
 * <p>Doc thang {@code user_entitlement.features} bang JDBC, cung khuon
 * {@code JdbcCatProfileLimitAdapter} da dung cho {@code max_cat_profiles} — KHONG import type
 * nao cua {@code com.catcheck.credit.*}. Ly do: {@code credit} chi expose named interface
 * {@code credit::api}, con {@code PlanFeature} nam o {@code credit.domain}; import xuyen vao do
 * la cach da duoc ghi nhan la no ky thuat trong repo nay, khong nen nhan them.</p>
 *
 * <p>p11 §11.5.5: KHONG cache ket qua trong phien — moi request kiem tra lai.</p>
 */
public interface FeatureEntitlementPort {

    /**
     * @param featureKey khoa trong cot JSONB {@code features}, vd {@code "trend"} — trung voi
     *                   {@code credit.domain.PlanFeature#jsonKey()}
     * @return {@code false} khi khong co dong entitlement nao, khoa vang mat, hoac gia tri
     *         la {@code false}/{@code "NONE"}
     */
    boolean isFeatureEnabled(UUID userId, String featureKey);
}
