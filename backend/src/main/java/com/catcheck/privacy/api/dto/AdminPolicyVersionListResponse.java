package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.PolicyVersionAdminService;

import java.util.List;

/**
 * L46 — danh sách phiên bản chính sách, phân trang kiểu <b>offset</b> (p8 §8.4.12 L46 cột
 * {@code O}): màn quản trị cần nhảy trang và cần tổng số dòng, hai thứ mà cursor không cho.
 */
public record AdminPolicyVersionListResponse(
        List<AdminPolicyVersionResponse> items,
        Integer page,
        Integer size,
        Long totalElements,
        Integer totalPages,
        Boolean hasMore
) {

    public static AdminPolicyVersionListResponse from(PolicyVersionAdminService.AdminPolicyPage page) {
        return new AdminPolicyVersionListResponse(
                page.rows().stream().map(AdminPolicyVersionResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages(),
                page.hasMore());
    }
}
