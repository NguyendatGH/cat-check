package com.catcheck.community.api;

import com.catcheck.community.api.dto.CommunityCommentRepliesResponse;
import com.catcheck.community.api.dto.CommunityCommentResponse;
import com.catcheck.community.api.dto.CommunityPageResponse;
import com.catcheck.community.api.dto.CommunityPostResponse;
import com.catcheck.community.api.dto.CommunityReactionRequest;
import com.catcheck.community.api.dto.CommunityReportRequest;
import com.catcheck.community.api.dto.CreateCommunityCommentRequest;
import com.catcheck.community.api.dto.CreateCommunityPostRequest;
import com.catcheck.community.application.CommunityCommentImageService;
import com.catcheck.community.application.CommunityPostImageService;
import com.catcheck.community.application.CommunityService;
import com.catcheck.shared.security.CurrentUser;
import com.catcheck.shared.security.SecurityPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community")
public class CommunityController {
    private final CommunityService service;
    private final CommunityPostImageService imageService;
    private final CommunityCommentImageService commentImageService;

    public CommunityController(CommunityService service, CommunityPostImageService imageService,
                               CommunityCommentImageService commentImageService) {
        this.service = service;
        this.imageService = imageService;
        this.commentImageService = commentImageService;
    }

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
        return new CommunityPostDetailResponse(CommunityPostResponse.from(result.post()), result.comments().stream()
                .map(thread -> CommunityCommentResponse.from(thread.comment(), thread.replies())).toList());
    }

    @PostMapping("/posts")
    @Operation(operationId = "createCommunityPost")
    public ResponseEntity<CommunityPostResponse> create(@CurrentUser SecurityPrincipal user,
                                                        @Valid @RequestBody CreateCommunityPostRequest request) {
        var post = service.createPost(user.userId(), request.category(), request.title(), request.body(), request.tags() == null ? List.of() : request.tags());
        return ResponseEntity.created(URI.create("/api/v1/community/posts/" + post.id())).body(CommunityPostResponse.from(post));
    }

    @PostMapping(value = "/posts/{postId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(operationId = "uploadCommunityPostImage", summary = "Gan anh cho bai viet cua chinh minh")
    public CommunityPostResponse uploadImage(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                             @RequestParam("file") MultipartFile file) {
        try {
            var post = imageService.replaceImage(user.userId(), postId, new CommunityPostImageService.ImageInput(
                    file.getContentType(), file.getInputStream(), file.getSize()));
            return CommunityPostResponse.from(post);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @GetMapping("/posts/{postId}/image")
    @Operation(operationId = "getCommunityPostImage", summary = "Anh dinh kem cua bai viet")
    public ResponseEntity<InputStreamResource> image(@PathVariable UUID postId) {
        var image = imageService.openImage(postId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(new InputStreamResource(image.content()));
    }

    @PostMapping(value = "/posts/{postId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createCommunityComment")
    public CommunityCommentResponse comment(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                            @Valid @RequestBody CreateCommunityCommentRequest request) {
        return CommunityCommentResponse.from(
                service.createComment(user.userId(), postId, request.parentCommentId(), request.body(), null));
    }

    @PostMapping(value = "/posts/{postId}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createCommunityCommentWithImage", summary = "Binh luan/tra loi kem toi da mot anh")
    public CommunityCommentResponse commentWithImage(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                                     @RequestParam(value = "body", required = false) String body,
                                                     @RequestParam(value = "parentCommentId", required = false) UUID parentCommentId,
                                                     @RequestParam(value = "file", required = false) MultipartFile file) {
        try {
            var image = file == null || file.isEmpty() ? null : new CommunityPostImageService.ImageInput(
                    file.getContentType(), file.getInputStream(), file.getSize());
            return CommunityCommentResponse.from(
                    commentImageService.createComment(user.userId(), postId, parentCommentId, body, image));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @GetMapping("/posts/{postId}/comments/{commentId}/replies")
    @Operation(operationId = "listCommunityCommentReplies", summary = "Cac phan hoi cua mot binh luan, phan trang")
    public CommunityCommentRepliesResponse replies(@CurrentUser SecurityPrincipal user,
                                                   @PathVariable UUID postId, @PathVariable UUID commentId,
                                                   @RequestParam(defaultValue = "0") int offset,
                                                   @RequestParam(defaultValue = "20") int limit) {
        var result = service.replies(user == null ? null : user.userId(), postId, commentId, offset, limit);
        return new CommunityCommentRepliesResponse(result.items().stream().map(CommunityCommentResponse::from).toList(),
                result.offset(), result.limit(), result.totalElements(), result.hasMore());
    }

    @GetMapping("/posts/{postId}/comments/{commentId}/image")
    @Operation(operationId = "getCommunityCommentImage", summary = "Anh dinh kem cua binh luan")
    public ResponseEntity<InputStreamResource> commentImage(@PathVariable UUID postId, @PathVariable UUID commentId) {
        var image = commentImageService.openImage(postId, commentId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .contentType(MediaType.parseMediaType(image.contentType()))
                .body(new InputStreamResource(image.content()));
    }

    @PostMapping("/posts/{postId}/comments/{commentId}/like")
    @Operation(operationId = "likeCommunityComment")
    public ReactionResponse likeComment(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                        @PathVariable UUID commentId) {
        return new ReactionResponse("LIKE", service.likeComment(user.userId(), postId, commentId, true));
    }

    @DeleteMapping("/posts/{postId}/comments/{commentId}/like")
    @Operation(operationId = "unlikeCommunityComment")
    public ReactionResponse unlikeComment(@CurrentUser SecurityPrincipal user, @PathVariable UUID postId,
                                          @PathVariable UUID commentId) {
        return new ReactionResponse("LIKE", service.likeComment(user.userId(), postId, commentId, false));
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
