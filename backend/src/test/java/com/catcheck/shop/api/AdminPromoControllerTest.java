package com.catcheck.shop.api;

import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import com.catcheck.shop.api.dto.AdminPromoCodeRequest;
import com.catcheck.shop.application.AdminPromoService;
import com.catcheck.shop.domain.PromoCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPromoControllerTest {

    private final AdminPromoService service = Mockito.mock(AdminPromoService.class);
    private final MockMvc mvc = AdminApiMockMvc.build(new AdminPromoController(service));

    @AfterEach
    void clearAuth() { AdminApiMockMvc.clear(); }

    private static PromoCode sample(UUID id, String code) {
        return new PromoCode(id, code, "Test Voucher", "PERCENT", 15, 0, null, null, 0, null, null, true, Instant.now(), Instant.now());
    }

    @Test
    void nonAdminIsForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/shop/promos"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_REQUIRED"));
    }

    @Test
    void adminCanListPromos() throws Exception {
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_SUPER");
        AdminApiMockMvc.authenticate(admin);
        UUID id = UUID.randomUUID();
        Mockito.when(service.list()).thenReturn(List.of(sample(id, "PROMO15")));

        mvc.perform(get("/api/v1/admin/shop/promos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("PROMO15"))
                .andExpect(jsonPath("$[0].amount").value(15));
    }

    @Test
    void adminCanCreatePromo() throws Exception {
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_CATALOG");
        AdminApiMockMvc.authenticate(admin);
        UUID id = UUID.randomUUID();
        Mockito.when(service.create(any(AdminPromoCodeRequest.class), eq(admin.userId()), eq("ADMIN_CATALOG")))
                .thenReturn(sample(id, "NEWYEAR2026"));

        mvc.perform(post("/api/v1/admin/shop/promos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "code": "NEWYEAR2026",
                            "label": "Giảm năm mới",
                            "type": "PERCENT",
                            "amount": 15,
                            "minOrderVnd": 100000
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("NEWYEAR2026"));
    }

    @Test
    void adminCanUpdatePromo() throws Exception {
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_SUPER");
        AdminApiMockMvc.authenticate(admin);
        UUID id = UUID.randomUUID();
        Mockito.when(service.update(eq(id), any(AdminPromoCodeRequest.class), eq(admin.userId()), eq("ADMIN_SUPER")))
                .thenReturn(sample(id, "PROMO15"));

        mvc.perform(put("/api/v1/admin/shop/promos/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {
                            "code": "PROMO15",
                            "label": "Updated",
                            "type": "PERCENT",
                            "amount": 15,
                            "minOrderVnd": 0
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PROMO15"));
    }

    @Test
    void adminCanToggleStatus() throws Exception {
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_SUPER");
        AdminApiMockMvc.authenticate(admin);
        UUID id = UUID.randomUUID();
        Mockito.when(service.setStatus(id, false, admin.userId(), "ADMIN_SUPER"))
                .thenReturn(new PromoCode(id, "P", "L", "PERCENT", 10, 0, null, null, 0, null, null, false, Instant.now(), Instant.now()));

        mvc.perform(patch("/api/v1/admin/shop/promos/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"isActive": false}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void adminCanDeletePromo() throws Exception {
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_SUPER");
        AdminApiMockMvc.authenticate(admin);
        UUID id = UUID.randomUUID();

        mvc.perform(delete("/api/v1/admin/shop/promos/{id}", id))
                .andExpect(status().isNoContent());

        Mockito.verify(service).delete(id, admin.userId(), "ADMIN_SUPER");
    }
}
