package com.catcheck.community.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CommunityPost(
        UUID id,
        UUID authorUserId,
        String authorName,
        String category,
        String title,
        String body,
        List<String> tags,
        String imageUrl,
        int likeCount,
        int commentCount,
        boolean likedByCurrentUser,
        boolean bookmarkedByCurrentUser,
        Instant createdAt) {
}
