package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityCommentRef;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CommunityService {
    /** Khớp COMMUNITY_REPORT_REASONS ở frontend và @Pattern của CommunityReportRequest. */
    static final java.util.Set<String> REPORT_REASONS = java.util.Set.of("SPAM", "MISLEADING", "HARASSMENT", "PRIVACY", "OTHER");

    static final int MAX_TOP_LEVEL_COMMENTS = 100;
    public static final int REPLY_PREVIEW_SIZE = 3;
    static final int MAX_COMMENT_LENGTH = 4000;

    private final CommunityRepository repository;
    private final UserAccountPort userAccountPort;
    private final CommunityRateLimiter rateLimiter;

    public CommunityService(CommunityRepository repository, UserAccountPort userAccountPort,
                             CommunityRateLimiter rateLimiter) {
        this.repository = repository;
        this.userAccountPort = userAccountPort;
        this.rateLimiter = rateLimiter;
    }

    public Page list(UUID viewerId, String category, int page, int size) {
        String normalized = category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)
                ? null : category.toUpperCase();
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 50);
        List<CommunityPost> items = repository.findPosts(viewerId, normalized, safePage * safeSize, safeSize);
        long total = repository.countPosts(normalized);
        return new Page(items, safePage, safeSize, total);
    }

    public Detail detail(UUID viewerId, UUID postId) {
        CommunityPost post = repository.findPost(viewerId, postId)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_NOT_FOUND));
        List<CommunityComment> comments = repository.findComments(postId, MAX_TOP_LEVEL_COMMENTS);
        List<UUID> withReplies = comments.stream().filter(c -> c.replyCount() > 0).map(CommunityComment::id).toList();
        Map<UUID, List<CommunityComment>> previews = repository.findReplyPreviews(withReplies, REPLY_PREVIEW_SIZE)
                .stream().collect(Collectors.groupingBy(CommunityComment::parentCommentId, LinkedHashMap::new, Collectors.toList()));
        return new Detail(post, comments.stream()
                .map(c -> new Thread(c, previews.getOrDefault(c.id(), List.of())))
                .toList());
    }

    /** Pages the replies of one top-level comment (the first {@link #REPLY_PREVIEW_SIZE} arrive with the post detail). */
    public Replies replies(UUID postId, UUID commentId, int offset, int limit) {
        CommunityCommentRef parent = repository.findComment(commentId)
                .filter(ref -> ref.postId().equals(postId) && ref.parentCommentId() == null)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.COMMENT_NOT_FOUND));
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.clamp(limit, 1, 50);
        return new Replies(repository.findReplies(parent.id(), safeOffset, safeLimit), safeOffset, safeLimit,
                repository.countReplies(parent.id()));
    }

    @Transactional
    public CommunityPost createPost(UUID authorId, String category, String title, String body, List<String> tags) {
        requireWriteAllowed(authorId);
        requireRateLimit(CommunityRateLimitRule.POST_CREATE_BY_USER, CommunityErrorCode.POST_RATE_LIMITED, authorId);
        return repository.insertPost(authorId, category.toUpperCase(), title.strip(), body.strip(), tags);
    }

    @Transactional
    public CommunityComment createComment(UUID authorId, UUID postId, String body) {
        return createComment(authorId, postId, null, body, null);
    }

    /**
     * Creates a comment or a reply. Replies stay one level deep: replying to a reply attaches the new
     * comment to the same top-level comment and records whom it answers. {@code imageRef} is a storage
     * key already validated and stored by {@link CommunityCommentImageService}.
     */
    @Transactional
    public CommunityComment createComment(UUID authorId, UUID postId, UUID parentCommentId, String body, String imageRef) {
        requireWriteAllowed(authorId);
        String text = body == null ? "" : body.strip();
        if (text.length() > MAX_COMMENT_LENGTH || (text.isEmpty() && imageRef == null)) {
            throw new BusinessRuleException(CommunityErrorCode.COMMENT_BODY_INVALID);
        }
        requireRateLimit(CommunityRateLimitRule.COMMENT_CREATE_BY_USER, CommunityErrorCode.COMMENT_RATE_LIMITED, authorId);
        if (repository.findPost(authorId, postId).isEmpty()) {
            throw new NotFoundException(CommunityErrorCode.POST_NOT_FOUND);
        }
        UUID rootId = null;
        UUID replyToUserId = null;
        if (parentCommentId != null) {
            CommunityCommentRef parent = repository.findComment(parentCommentId)
                    .filter(ref -> ref.postId().equals(postId))
                    .orElseThrow(() -> new NotFoundException(CommunityErrorCode.COMMENT_NOT_FOUND));
            rootId = parent.rootId();
            if (parent.parentCommentId() != null) {
                replyToUserId = parent.authorUserId();
            }
        }
        return repository.insertComment(authorId, postId, rootId, replyToUserId, text, imageRef);
    }

    @Transactional
    public boolean reaction(UUID userId, UUID postId, String reaction, boolean active) {
        requireWriteAllowed(userId);
        if (repository.findPost(userId, postId).isEmpty()) {
            throw new NotFoundException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return repository.toggleReaction(userId, postId, reaction.toUpperCase(), active);
    }

    @Transactional
    public void report(UUID userId, UUID postId, UUID commentId, String reason, String details) {
        requireWriteAllowed(userId);
        if (reason == null || !REPORT_REASONS.contains(reason)) {
            // GlobalExceptionHandler map sang VALIDATION_FAILED (400); không tiêu hao hạn mức báo cáo.
            throw new IllegalArgumentException("community report reason không hợp lệ");
        }
        requireRateLimit(CommunityRateLimitRule.REPORT_CREATE_BY_USER, CommunityErrorCode.REPORT_RATE_LIMITED, userId);
        if ((postId == null) == (commentId == null)) {
            throw new com.catcheck.shared.error.BusinessRuleException(CommunityErrorCode.REPORT_TARGET_REQUIRED);
        }
        UUID authorId = repository.findReportTargetAuthor(postId, commentId)
                .orElseThrow(() -> new NotFoundException(
                        postId != null ? CommunityErrorCode.POST_NOT_FOUND : CommunityErrorCode.REPORT_NOT_FOUND));
        if (authorId.equals(userId)) {
            throw new com.catcheck.shared.error.BusinessRuleException(CommunityErrorCode.REPORT_OWN_CONTENT);
        }
        if (!repository.report(userId, postId, commentId, reason, details)) {
            throw new com.catcheck.shared.error.ConflictException(CommunityErrorCode.REPORT_DUPLICATE);
        }
    }

    private void requireWriteAllowed(UUID userId) {
        AccountStatus status = userAccountPort.statusOf(userId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CommunityErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    private void requireRateLimit(CommunityRateLimitRule rule, CommunityErrorCode errorCode, UUID userId) {
        CommunityRateLimiter.RetryAfter retryAfter = rateLimiter.rejectIfLimited(rule, userId == null ? null : userId.toString());
        if (retryAfter != null) {
            throw new RateLimitedException(errorCode, retryAfter.headerSeconds(), rule.name());
        }
    }

    public record Page(List<CommunityPost> items, int page, int size, long totalElements) {
        public int totalPages() { return size == 0 ? 0 : (int) Math.ceilDiv(totalElements, size); }
        public boolean hasMore() { return (long) (page + 1) * size < totalElements; }
    }

    public record Detail(CommunityPost post, List<Thread> comments) { }

    public record Thread(CommunityComment comment, List<CommunityComment> replies) { }

    public record Replies(List<CommunityComment> items, int offset, int limit, long totalElements) {
        public boolean hasMore() { return (long) offset + items.size() < totalElements; }
    }
}
