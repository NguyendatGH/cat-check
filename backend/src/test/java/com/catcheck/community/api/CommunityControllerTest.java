package com.catcheck.community.api;

import com.catcheck.community.application.CommunityService;
import com.catcheck.shared.testing.AdminApiMockMvc;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommunityControllerTest {

    private final MockMvc mvc = AdminApiMockMvc.build(new CommunityController(Mockito.mock(CommunityService.class)));

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
