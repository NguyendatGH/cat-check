package com.catcheck.community.api;

import com.catcheck.community.application.CommunityCommentImageService;
import com.catcheck.community.application.CommunityPostImageService;
import com.catcheck.community.application.CommunityService;
import com.catcheck.community.domain.CommunityComment;
import com.catcheck.community.domain.CommunityPost;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayInputStream;
import java.util.List;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityControllerTest {

    private final CommunityService service = Mockito.mock(CommunityService.class);
    private final CommunityPostImageService imageService = Mockito.mock(CommunityPostImageService.class);
    private final CommunityCommentImageService commentImageService = Mockito.mock(CommunityCommentImageService.class);
    private final MockMvc mvc = AdminApiMockMvc.build(new CommunityController(service, imageService, commentImageService));

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    void createCommentReturns201LikeCreatePost() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles("USER");
        UUID postId = UUID.randomUUID();
        AdminApiMockMvc.authenticate(user);
        Mockito.when(service.createComment(eq(user.userId()), eq(postId), any(), any(), any())).thenReturn(
                new CommunityComment(UUID.randomUUID(), postId, user.userId(), "A", "Noi dung", Instant.parse("2026-10-06T10:00:00Z")));

        mvc.perform(post("/api/v1/community/posts/{postId}/comments", postId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Noi dung\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Noi dung"));
    }

    @Test
    void invalidPostCategoryIs400() throws Exception {
        for (String category : new String[]{"ALL", "foo"}) {
            mvc.perform(post("/api/v1/community/posts").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"category\":\"" + category + "\",\"title\":\"t\",\"body\":\"b\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.violations[0].field").value("category"));
        }
    }

    @Test
    void invalidReportReasonIs400() throws Exception {
        mvc.perform(post("/api/v1/community/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postId\":\"01990000-0000-7000-8000-000000000001\",\"reason\":\"banana\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("reason"));
    }

    @Test
    void uploadImageReturnsPostWithStableImageUrl() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles("USER");
        UUID postId = UUID.randomUUID();
        AdminApiMockMvc.authenticate(user);
        Mockito.when(imageService.replaceImage(eq(user.userId()), eq(postId), any())).thenReturn(new CommunityPost(
                postId, user.userId(), "A", "TIP", "T", "B", List.of(), "community-post/2026/10/14/x.png",
                0, 0, false, false, Instant.parse("2026-10-14T10:00:00Z")));

        mvc.perform(multipart("/api/v1/community/posts/{postId}/image", postId)
                        .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageUrl").value("/api/v1/community/posts/" + postId + "/image"));
    }

    @Test
    void imageEndpointStreamsStoredBytes() throws Exception {
        UUID postId = UUID.randomUUID();
        Mockito.when(imageService.openImage(postId)).thenReturn(
                new CommunityPostImageService.ImageContent(new ByteArrayInputStream(new byte[]{7, 8}), "image/webp"));

        mvc.perform(get("/api/v1/community/posts/{postId}/image", postId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/webp"))
                .andExpect(content().bytes(new byte[]{7, 8}));
    }

    @Test
    void multipartCommentWithImageAndParentIsAccepted() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles("USER");
        UUID postId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        AdminApiMockMvc.authenticate(user);
        Mockito.when(commentImageService.createComment(eq(user.userId()), eq(postId), eq(parentId), eq(""), any()))
                .thenReturn(new CommunityComment(commentId, postId, user.userId(), "A", "", Instant.parse("2026-10-14T10:00:00Z"),
                        parentId, "B", "community-comment/2026/10/14/x.png", 0));

        mvc.perform(multipart("/api/v1/community/posts/{postId}/comments", postId)
                        .file(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3}))
                        .param("body", "")
                        .param("parentCommentId", parentId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentCommentId").value(parentId.toString()))
                .andExpect(jsonPath("$.replyToName").value("B"))
                .andExpect(jsonPath("$.imageUrl").value("/api/v1/community/posts/" + postId + "/comments/" + commentId + "/image"));
    }

    @Test
    void detailNestsReplyPreviewsUnderTheirComment() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-14T10:00:00Z");
        var root = new CommunityComment(rootId, postId, UUID.randomUUID(), "A", "Goc", at, null, null, null, 5);
        var reply = new CommunityComment(UUID.randomUUID(), postId, UUID.randomUUID(), "B", "Tra loi", at, rootId, null, null, 0);
        Mockito.when(service.detail(any(), eq(postId))).thenReturn(new CommunityService.Detail(
                new CommunityPost(postId, UUID.randomUUID(), "A", "TIP", "T", "B", List.of(), null, 0, 6, false, false, at),
                List.of(new CommunityService.Thread(root, List.of(reply)))));

        mvc.perform(get("/api/v1/community/posts/{postId}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comments[0].replyCount").value(5))
                .andExpect(jsonPath("$.comments[0].replies[0].body").value("Tra loi"))
                .andExpect(jsonPath("$.comments[0].replies[0].parentCommentId").value(rootId.toString()));
    }

    @Test
    void repliesEndpointPagesReplies() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        var reply = new CommunityComment(UUID.randomUUID(), postId, UUID.randomUUID(), "B", "R4",
                Instant.parse("2026-10-14T10:00:00Z"), rootId, null, null, 0);
        Mockito.when(service.replies(postId, rootId, 3, 20))
                .thenReturn(new CommunityService.Replies(List.of(reply), 3, 20, 5));

        mvc.perform(get("/api/v1/community/posts/{postId}/comments/{commentId}/replies", postId, rootId)
                        .param("offset", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].body").value("R4"))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.hasMore").value(true));
    }
}
