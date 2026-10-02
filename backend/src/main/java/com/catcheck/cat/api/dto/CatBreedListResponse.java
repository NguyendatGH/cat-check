package com.catcheck.cat.api.dto;

import java.util.List;

/** F2 — envelope không phân trang (danh mục giống mèo hữu hạn, seed một lần ở V7). */
public record CatBreedListResponse(List<CatBreedResponse> items) {
}
