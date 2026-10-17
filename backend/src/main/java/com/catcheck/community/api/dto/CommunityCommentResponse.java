package com.catcheck.community.api.dto;

import com.catcheck.community.domain.CommunityComment;

import java.time.Instant;
import java.util.List;

public record CommunityCommentResponse(
        String id, String postId, String parentCommentId, String authorName, String replyToName, String body,
        String imageUrl, int replyCount, int likeCount, boolean likedByCurrentUser,
        List<CommunityCommentResponse> replies, Instant createdAt) {

    public static CommunityCommentResponse from(CommunityComment value) {
        return from(value, List.of());
    }

    public static CommunityCommentResponse from(CommunityComment value, List<CommunityComment> replies) {
        return new CommunityCommentResponse(value.id().toString(), value.postId().toString(),
                value.parentCommentId() == null ? null : value.parentCommentId().toString(),
                value.authorName(), value.replyToName(), value.body(), publicImageUrl(value), value.replyCount(),
                value.likeCount(), value.likedByCurrentUser(), replies.stream().map(CommunityCommentResponse::from).toList(), value.createdAt());
    }

    // image_url holds a storage key; it is served through the comment image endpoint.
    private static String publicImageUrl(CommunityComment value) {
        String ref = value.imageUrl();
        if (ref == null || ref.isBlank()) return null;
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("/")) return ref;
        return "/api/v1/community/posts/" + value.postId() + "/comments/" + value.id() + "/image";
    }
}
