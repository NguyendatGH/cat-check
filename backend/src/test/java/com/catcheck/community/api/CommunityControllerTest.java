package com.catcheck.community.api;

import com.catcheck.community.application.CommunityService;
import com.catcheck.community.domain.CommunityComment;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityControllerTest {

    private final CommunityService service = Mockito.mock(CommunityService.class);
    private final MockMvc mvc = AdminApiMockMvc.build(new CommunityController(service));

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    void createCommentReturns201LikeCreatePost() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles("USER");
        UUID postId = UUID.randomUUID();
        AdminApiMockMvc.authenticate(user);
        Mockito.when(service.createComment(eq(user.userId()), eq(postId), any())).thenReturn(
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
}
