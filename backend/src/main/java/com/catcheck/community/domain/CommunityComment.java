package com.catcheck.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CommunityComment(
        UUID id,
        UUID postId,
        UUID authorUserId,
        String authorName,
        String body,
        Instant createdAt) {
}
