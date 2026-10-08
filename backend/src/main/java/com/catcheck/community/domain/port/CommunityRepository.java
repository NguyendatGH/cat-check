package com.catcheck.community.domain.port;

import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.CommunityReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository {
    List<CommunityPost> findPosts(UUID viewerId, String category, int offset, int limit);

    long countPosts(String category);

    Optional<CommunityPost> findPost(UUID viewerId, UUID postId);

    List<CommunityComment> findComments(UUID postId, int limit);

    CommunityPost insertPost(UUID authorId, String category, String title, String body, List<String> tags);

    CommunityComment insertComment(UUID authorId, UUID postId, String body);

    boolean toggleReaction(UUID userId, UUID postId, String reaction, boolean active);

    boolean reportTargetExists(UUID postId, UUID commentId);

    /** @return false nếu người này đã có báo cáo còn mở cho cùng đối tượng. */
    boolean report(UUID userId, UUID postId, UUID commentId, String reason, String details);

    List<CommunityReport> findReports(String status, int offset, int limit);

    long countReports(String status);

    java.util.Optional<CommunityReport> findReport(UUID reportId);

    void moderateReport(UUID reportId, String action);
}
