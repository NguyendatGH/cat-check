package com.catcheck.community.api.dto;

import com.catcheck.community.domain.CommunityPost;

import java.time.Instant;
import java.util.List;

public record CommunityPostResponse(
        String id, String authorName, String category, String title, String body, List<String> tags,
        String imageUrl, int likeCount, int commentCount, boolean liked, boolean bookmarked, Instant createdAt) {
    public static CommunityPostResponse from(CommunityPost value) {
        return new CommunityPostResponse(value.id().toString(), value.authorName(), value.category(), value.title(),
                value.body(), value.tags(), value.imageUrl(), value.likeCount(), value.commentCount(),
                value.likedByCurrentUser(), value.bookmarkedByCurrentUser(), value.createdAt());
    }
}
