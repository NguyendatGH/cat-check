package com.catcheck.community.api;

import com.catcheck.community.api.dto.CommunityCommentResponse;
import com.catcheck.community.api.dto.CommunityPageResponse;
import com.catcheck.community.api.dto.CommunityPostResponse;
import com.catcheck.community.api.dto.CommunityReactionRequest;
import com.catcheck.community.api.dto.CommunityReportRequest;
import com.catcheck.community.api.dto.CreateCommunityCommentRequest;
import com.catcheck.community.api.dto.CreateCommunityPostRequest;
import com.catcheck.community.application.CommunityService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityController {
    private final CommunityService service;

    public CommunityController(CommunityService service) { this.service = service; }

    @GetMapping("/posts")
    @Operation(operationId = "listCommunityPosts")
    public CommunityPageResponse list(@CurrentUser SecurityPrincipal user,
                                      @RequestParam(required = false) String category,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var result = service.list(user == null ? null : user.userId(), category, page, size);
        return new CommunityPageResponse(result.items().stream().map(CommunityPostResponse::from).toList(),
                result.page(), result.size(), result.totalElements(), result.totalPages(), result.hasMore());
    }

    @GetMapping("/posts/{postId}")
    @Operation(operationId = "getCommunityPost")
    public CommunityPostDetailResponse detail(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId) {
        var result = service.detail(user == null ? null : user.userId(), postId);
        return new CommunityPostDetailResponse(CommunityPostResponse.from(result.post()), result.comments().stream().map(CommunityCommentResponse::from).toList());
    }

    @PostMapping("/posts")
    @Operation(operationId = "createCommunityPost")
    public ResponseEntity<CommunityPostResponse> create(@CurrentUser SecurityPrincipal user,
                                                        @Valid @RequestBody CreateCommunityPostRequest request) {
        var post = service.createPost(user.userId(), request.category(), request.title(), request.body(), request.tags() == null ? List.of() : request.tags());
        return ResponseEntity.created(URI.create("/api/v1/community/posts/" + post.id())).body(CommunityPostResponse.from(post));
    }

    @PostMapping("/posts/{postId}/comments")
    @Operation(operationId = "createCommunityComment")
    public CommunityCommentResponse comment(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                            @Valid @RequestBody CreateCommunityCommentRequest request) {
        return CommunityCommentResponse.from(service.createComment(user.userId(), postId, request.body()));
    }

    @PostMapping("/posts/{postId}/reactions")
    @Operation(operationId = "setCommunityReaction")
    public ReactionResponse reaction(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                     @Valid @RequestBody CommunityReactionRequest request) {
        return new ReactionResponse(request.reaction().toUpperCase(), service.reaction(user.userId(), postId, request.reaction(), request.active()));
    }

    @DeleteMapping("/posts/{postId}/reactions/{reaction}")
    @Operation(operationId = "deleteCommunityReaction")
    public ResponseEntity<Void> removeReaction(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId, @PathVariable String reaction) {
        service.reaction(user.userId(), postId, reaction, false);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports")
    @Operation(operationId = "reportCommunityContent")
    public ResponseEntity<Void> report(@CurrentUser SecurityPrincipal user, @Valid @RequestBody CommunityReportRequest request) {
        service.report(user.userId(), request.postId(), request.commentId(), request.reason(), request.details());
        return ResponseEntity.noContent().build();
    }

    public record CommunityPostDetailResponse(CommunityPostResponse post, List<CommunityCommentResponse> comments) { }
    public record ReactionResponse(String reaction, boolean active) { }
}
