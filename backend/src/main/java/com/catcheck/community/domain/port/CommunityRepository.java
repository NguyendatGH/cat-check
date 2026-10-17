package com.catcheck.community.domain.port;

import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityCommentRef;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.CommunityReport;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityRepository {
    List<CommunityPost> findPosts(UUID viewerId, String category, int offset, int limit);

    long countPosts(String category);

    Optional<CommunityPost> findPost(UUID viewerId, UUID postId);

    /** Top-level published comments of a post, oldest first, each with its published reply count. */
    List<CommunityComment> findComments(UUID postId, int limit);

    /** The first {@code perParent} published replies of each given top-level comment, oldest first. */
    List<CommunityComment> findReplyPreviews(List<UUID> parentCommentIds, int perParent);

    List<CommunityComment> findReplies(UUID parentCommentId, int offset, int limit);

    long countReplies(UUID parentCommentId);

    /** A published comment on a published post. */
    Optional<CommunityCommentRef> findComment(UUID commentId);

    CommunityPost insertPost(UUID authorId, String category, String title, String body, List<String> tags);

    void updatePostImage(UUID postId, String imageRef);

    /** Empty when the post is no longer published at insert time (e.g. hidden concurrently). */
    Optional<CommunityComment> insertComment(UUID authorId, UUID postId, UUID parentCommentId, UUID replyToUserId,
                                             String body, String imageRef);

    /** Which of {@code commentIds} the viewer has liked. */
    java.util.Set<UUID> findLikedCommentIds(UUID viewerId, List<UUID> commentIds);

    /** Idempotent like/unlike of a comment. @return the like's resulting state. */
    boolean setCommentLike(UUID userId, UUID commentId, boolean active);

    /** @return the reaction's resulting state ({@code true} = present) after the call. */
    boolean toggleReaction(UUID userId, UUID postId, String reaction, boolean active);

    Optional<UUID> findReportTargetAuthor(UUID postId, UUID commentId);

    /** Author of the post/comment whatever its moderation status (unlike {@link #findReportTargetAuthor}). */
    Optional<UUID> findContentAuthor(UUID postId, UUID commentId);

    boolean report(UUID userId, UUID postId, UUID commentId, String reason, String details);

    List<CommunityReport> findReports(String status, int offset, int limit);

    long countReports(String status);

    java.util.Optional<CommunityReport> findReport(UUID reportId);

    /** Like {@link #findReport} but row-locks the report until the transaction ends. */
    Optional<CommunityReport> findReportForUpdate(UUID reportId);

    void moderateReport(UUID reportId, String action);
}
