package com.catcheck.community.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A community comment. Replies are one level deep: {@code parentCommentId} is always a top-level
 * comment. {@code imageUrl} holds the raw storage reference; {@code replyCount} is only populated
 * for top-level comments. {@code likedByCurrentUser} is false for anonymous viewers.
 */
public record CommunityComment(
        UUID id,
        UUID postId,
        UUID authorUserId,
        String authorName,
        String body,
        Instant createdAt,
        UUID parentCommentId,
        String replyToName,
        String imageUrl,
        int replyCount,
        int likeCount,
        boolean likedByCurrentUser) {

    public CommunityComment(UUID id, UUID postId, UUID authorUserId, String authorName, String body, Instant createdAt,
                            UUID parentCommentId, String replyToName, String imageUrl, int replyCount) {
        this(id, postId, authorUserId, authorName, body, createdAt, parentCommentId, replyToName, imageUrl, replyCount,
                0, false);
    }

    public CommunityComment(UUID id, UUID postId, UUID authorUserId, String authorName, String body, Instant createdAt) {
        this(id, postId, authorUserId, authorName, body, createdAt, null, null, null, 0);
    }

    public CommunityComment withLiked(boolean liked) {
        return new CommunityComment(id, postId, authorUserId, authorName, body, createdAt, parentCommentId, replyToName,
                imageUrl, replyCount, likeCount, liked);
    }
}
