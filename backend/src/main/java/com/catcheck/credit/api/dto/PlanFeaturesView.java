package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.HistoryLevel;
import com.catcheck.credit.domain.PackagePlan;
import com.catcheck.credit.domain.PlanFeatures;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Khối {@code package_plan.features} trên dây — dùng cho cả đọc (L25) và ghi (L26).
 *
 * <p>{@code history} là chuỗi khớp <b>từng ký tự</b> với enum trong DB
 * ({@code HistoryLevel}) theo quy tắc cứng #5 của CLAUDE.md: enum trên dây = enum trong DB.</p>
 *
 * <p>Bốn cờ là {@code Boolean} + {@code @NotNull}, không phải {@code boolean}: L26 ghi đè CẢ
 * khối {@code features} nên thiếu một cờ là mất tính năng đó một cách im lặng — phải trả
 * {@code 400} với tên trường. Và với record, một primitive vắng mặt làm Jackson 3 ném
 * {@code HttpMessageNotReadableException} ⇒ 500 chứ không phải 400 (xem
 * {@link UpdatePackagePlanRequest}).</p>
 */
public record PlanFeaturesView(
        @NotBlank @Pattern(regexp = "NONE|BASIC|ADVANCED") String history,
        @NotNull Boolean trend,
        @NotNull Boolean reminder,
        @NotNull Boolean export,
        @NotNull Boolean storeImage
) {

    public static PlanFeaturesView from(PackagePlan plan) {
        PlanFeatures features = plan.features();
        return new PlanFeaturesView(
                features.history().name(),
                features.trend(),
                features.reminder(),
                features.export(),
                features.storeImage());
    }

    /** Dạng domain để ghi xuống JSONB. Giá trị {@code history} lạ ⇒ {@code IllegalArgumentException} ⇒ 400. */
    public PlanFeatures toDomain() {
        return new PlanFeatures(HistoryLevel.valueOf(history),
                Boolean.TRUE.equals(trend), Boolean.TRUE.equals(reminder),
                Boolean.TRUE.equals(export), Boolean.TRUE.equals(storeImage));
    }
}
