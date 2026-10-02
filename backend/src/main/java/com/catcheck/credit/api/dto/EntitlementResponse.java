package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.PlanFeature;

import java.time.Instant;

/**
 * {@code GET /api/v1/entitlements/me} (p8 H4).
 *
 * @param currentPackage     gói cao nhất từng kích hoạt
 * @param maxCatProfiles     hạn mức hồ sơ mèo, {@code null} = không giới hạn
 * @param hasHistory         quyền xem lịch sử
 * @param hasTrend           quyền xem biểu đồ xu hướng
 * @param hasReminder        quyền đặt nhắc theo dõi
 * @param hasExport          quyền xuất hồ sơ PDF
 * @param storeImage         quyền lưu ảnh gốc
 * @param writeAccessUntil   hết hạn quyền tạo mới, {@code null} nếu đã hết
 * @param trialScansRemaining số lượt trial còn lại
 */
public record EntitlementResponse(
        String currentPackage,
        Integer maxCatProfiles,
        boolean hasHistory,
        boolean hasTrend,
        boolean hasReminder,
        boolean hasExport,
        boolean storeImage,
        Instant writeAccessUntil,
        int trialScansRemaining
) {

    public static EntitlementResponse from(Entitlement entitlement, int trialScanLimit) {
        return new EntitlementResponse(
                entitlement.highestPackage(),
                entitlement.maxCatProfiles(),
                entitlement.isFeatureEnabled(PlanFeature.HISTORY),
                entitlement.isFeatureEnabled(PlanFeature.TREND),
                entitlement.isFeatureEnabled(PlanFeature.REMINDER),
                entitlement.isFeatureEnabled(PlanFeature.EXPORT),
                entitlement.isFeatureEnabled(PlanFeature.STORE_IMAGE),
                entitlement.writeAccessUntil(),
                Math.max(0, trialScanLimit - entitlement.trialScansUsed()));
    }
}
