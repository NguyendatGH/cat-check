package com.catcheck.community.api.dto;

import com.catcheck.community.domain.CommunityPost;

import java.time.Instant;
import java.util.List;

public record CommunityPostResponse(
        String id, String authorName, String category, String title, String body, List<String> tags,
        String imageUrl, int likeCount, int commentCount, boolean liked, boolean bookmarked, Instant createdAt) {
    public static CommunityPostResponse from(CommunityPost value) {
        return new CommunityPostResponse(value.id().toString(), value.authorName(), value.category(), value.title(),
                value.body(), value.tags(), publicImageUrl(value), value.likeCount(), value.commentCount(),
                value.likedByCurrentUser(), value.bookmarkedByCurrentUser(), value.createdAt());
    }

    // image_url holds a storage key for uploaded images; legacy absolute/relative URLs pass through.
    private static String publicImageUrl(CommunityPost value) {
        String ref = value.imageUrl();
        if (ref == null || ref.isBlank()) return null;
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("/")) return ref;
        return "/api/v1/community/posts/" + value.id() + "/image";
    }
}
