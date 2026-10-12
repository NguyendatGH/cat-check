package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.PolicyVersionAdminService;

import java.util.List;

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
