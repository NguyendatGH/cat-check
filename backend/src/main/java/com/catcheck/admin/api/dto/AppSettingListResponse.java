package com.catcheck.admin.api.dto;

import java.util.List;

/**
 * L69 — boc danh sach trong mot object thay vi tra mang tran.
 *
 * <p>p8 §8.1.4 ghi L69 la {@code —} (khong phan trang): bang chi vai chuc dong (p4 §H3 "bang vai
 * chuc dong"). Van boc vi mot mang JSON tran o muc cao nhat la chỗ khong the them truong ve sau
 * ma khong pha client — va cung khuon voi {@code AdminOpsPageResponse} cua L64/L66.
 */
public record AppSettingListResponse(List<AppSettingResponse> items, Integer total) {

    public static AppSettingListResponse of(List<AppSettingResponse> items) {
        return new AppSettingListResponse(items, items.size());
    }
}
