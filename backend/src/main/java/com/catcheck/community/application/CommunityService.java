package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CommunityService {
    private final CommunityRepository repository;

    public CommunityService(CommunityRepository repository) {
        this.repository = repository;
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
        return repository.insertPost(authorId, category.toUpperCase(), title.strip(), body.strip(), tags);
    }

    @Transactional
    public CommunityComment createComment(UUID authorId, UUID postId, String body) {
        if (repository.findPost(authorId, postId).isEmpty()) {
            throw new NotFoundException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return repository.insertComment(authorId, postId, body.strip());
    }

    @Transactional
    public boolean reaction(UUID userId, UUID postId, String reaction, boolean active) {
        if (repository.findPost(userId, postId).isEmpty()) {
            throw new NotFoundException(CommunityErrorCode.POST_NOT_FOUND);
        }
        return repository.toggleReaction(userId, postId, reaction.toUpperCase(), active);
    }

    @Transactional
    public void report(UUID userId, UUID postId, UUID commentId, String reason, String details) {
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

    public record Page(List<CommunityPost> items, int page, int size, long totalElements) {
        public int totalPages() { return size == 0 ? 0 : (int) Math.ceilDiv(totalElements, size); }
        public boolean hasMore() { return (long) (page + 1) * size < totalElements; }
    }

    public record Detail(CommunityPost post, List<CommunityComment> comments) { }
}
