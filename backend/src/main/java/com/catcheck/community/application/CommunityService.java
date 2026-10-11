package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.CommunityRateLimitRule;
import com.catcheck.community.domain.port.CommunityRateLimiter;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CommunityService {
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
        return new Detail(post, repository.findComments(postId, 100));
    }

    @Transactional
    public CommunityPost createPost(UUID authorId, String category, String title, String body, List<String> tags) {
        requireWriteAllowed(authorId);
        requireRateLimit(CommunityRateLimitRule.POST_CREATE_BY_USER, CommunityErrorCode.POST_RATE_LIMITED, authorId);
        return repository.insertPost(authorId, category.toUpperCase(), title.strip(), body.strip(), tags);
    }

    @Transactional
    public CommunityComment createComment(UUID authorId, UUID postId, String body) {
        requireWriteAllowed(authorId);
        requireRateLimit(CommunityRateLimitRule.COMMENT_CREATE_BY_USER, CommunityErrorCode.COMMENT_RATE_LIMITED, authorId);
        if (repository.findPost(authorId, postId).isEmpty()) {
            throw new NotFoundException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return repository.insertComment(authorId, postId, body.strip());
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
        requireRateLimit(CommunityRateLimitRule.REPORT_CREATE_BY_USER, CommunityErrorCode.REPORT_RATE_LIMITED, userId);
        if ((postId == null) == (commentId == null)) {
            throw new com.catcheck.shared.error.BusinessRuleException(CommunityErrorCode.REPORT_TARGET_REQUIRED);
        }
        if (!repository.reportTargetExists(postId, commentId)) {
            throw new NotFoundException(postId != null ? CommunityErrorCode.POST_NOT_FOUND : CommunityErrorCode.REPORT_NOT_FOUND);
        }
        if (!repository.report(userId, postId, commentId, reason, details)) {
            throw new com.catcheck.shared.error.ConflictException(CommunityErrorCode.REPORT_DUPLICATE);
        }
    }

    /**
     * I19 (p4 §4.4.9) của p8 §8.3.2: tài khoản {@code RESTRICTED} bị chặn mọi thao tác ghi.
     *
     * <p>Trước bản sửa này, {@code CommunityService} không kiểm trạng thái tài khoản ở BẤT KỲ
     * luồng ghi nào (đăng bài, bình luận, reaction, report) — một tài khoản đã bật "hạn chế xử
     * lý" (p15 §15.4.7) vẫn đăng bài/bình luận/report bình thường, vi phạm I19. Cùng khuôn với
     * {@code cat.application.CatNoteService.requireWriteAllowed}.</p>
     */
    private void requireWriteAllowed(UUID userId) {
        AccountStatus status = userAccountPort.statusOf(userId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CommunityErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    /** Cùng khuôn với {@code credit.application.ActivateCreditCodeService.requireRateLimit}. */
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

    public record Detail(CommunityPost post, List<CommunityComment> comments) { }
}
