package com.catcheck.community.domain;

import java.util.UUID;

/** Minimal view of a published comment, used to validate replies and serve comment images. */
public record CommunityCommentRef(UUID id, UUID postId, UUID authorUserId, UUID parentCommentId, String imageUrl) {
    public UUID rootId() {
        return parentCommentId == null ? id : parentCommentId;
    }
}
