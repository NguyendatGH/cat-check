package com.catcheck.community.api.dto;

import java.util.List;

public record CommunityReportPageResponse(List<CommunityReportResponse> items, int page, int size,
                                          long totalElements, int totalPages, boolean hasMore) { }
