package com.catcheck.credit.api.dto;

import java.util.List;

/**
 * F3 — {@code GET /reference/packages}. Bọc trong {@code items} giống F1/F2 (danh mục công
 * khai không phân trang, p8 §8.4.6).
 */
public record PackagePlanListResponse(List<PackagePlanResponse> items) {
}
