package com.catcheck.community.api.dto;

import java.util.List;

public record CommunityPageResponse(List<CommunityPostResponse> items, int page, int size, long totalElements, int totalPages, boolean hasMore) { }
