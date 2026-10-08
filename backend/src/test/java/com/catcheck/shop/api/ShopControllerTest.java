package com.catcheck.shop.api;

import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shop.application.ShopService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 405 va 400 kieu tham so — truoc day deu roi vao handler 500. */
class ShopControllerTest {

    private final MockMvc mvc = AdminApiMockMvc.build(new ShopController(Mockito.mock(ShopService.class)));

    @Test
    void wrongMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void nonUuidPathVariableIs400() throws Exception {
        mvc.perform(get("/api/v1/shop/products/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("productId"));
    }
}
