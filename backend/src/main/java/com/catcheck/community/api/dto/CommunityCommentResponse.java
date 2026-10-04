package com.catcheck.community.api.dto;

import com.catcheck.community.domain.CommunityComment;

import java.time.Instant;

public record CommunityCommentResponse(String id, String postId, String authorName, String body, Instant createdAt) {
    public static CommunityCommentResponse from(CommunityComment value) {
        return new CommunityCommentResponse(value.id().toString(), value.postId().toString(), value.authorName(), value.body(), value.createdAt());
    }
}
