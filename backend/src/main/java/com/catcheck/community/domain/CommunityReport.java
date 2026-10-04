package com.catcheck.community.domain;

import java.time.Instant;
import java.util.UUID;

public record CommunityReport(UUID id, UUID postId, UUID commentId, String reporterName,
                              String reason, String details, String status, String targetTitle,
                              Instant createdAt) {
    public String targetType() { return postId != null ? "POST" : "COMMENT"; }
    public UUID targetId() { return postId != null ? postId : commentId; }
}
