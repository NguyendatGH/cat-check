package com.catcheck.admin.api.dto;

import java.util.List;

public record AppSettingListResponse(List<AppSettingResponse> items, Integer total) {

    public static AppSettingListResponse of(List<AppSettingResponse> items) {
        return new AppSettingListResponse(items, items.size());
    }
}
