package com.catcheck.community.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A community comment. Replies are one level deep: {@code parentCommentId} is always a top-level
 * comment. {@code imageUrl} holds the raw storage reference; {@code replyCount} is only populated
 * for top-level comments.
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
        int replyCount) {

    public CommunityComment(UUID id, UUID postId, UUID authorUserId, String authorName, String body, Instant createdAt) {
        this(id, postId, authorUserId, authorName, body, createdAt, null, null, null, 0);
    }
}
