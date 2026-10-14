package com.catcheck.community.api.dto;

import java.util.List;

public record CommunityCommentRepliesResponse(List<CommunityCommentResponse> items, int offset, int limit,
                                              long totalElements, boolean hasMore) { }
