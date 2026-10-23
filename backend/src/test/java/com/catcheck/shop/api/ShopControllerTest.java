package com.catcheck.shop.api;

import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import com.catcheck.shop.application.ShopService;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShopControllerTest {

    private final ShopService service = Mockito.mock(ShopService.class);
    private final MockMvc mvc = AdminApiMockMvc.build(new ShopController(service));

    @AfterEach
    void clearAuthentication() { AdminApiMockMvc.clear(); }

    @Test
    void wrongMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(get("/api/v1/orders/{id}/cancel", UUID.randomUUID()))
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

    @Test
    void unknownPaymentMethodIs400NotDatabase500() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"paymentMethod":"PAYPAL","receiverName":"A","receiverPhone":"0900000000","shippingAddress":"B"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("paymentMethod"));
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void momoIsNoLongerAnAcceptedPaymentMethod() throws Exception {
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"paymentMethod":"MOMO","receiverName":"A","receiverPhone":"0900000000","shippingAddress":"B"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("paymentMethod"));
        Mockito.verifyNoInteractions(service);
    }

    @Test
    void checkoutWithPromoCodePassesCodeToService() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles();
        AdminApiMockMvc.authenticate(user);
        Order created = order("PENDING_PAYMENT", null);
        Mockito.when(service.checkout(user.userId(), "COD", "A", "0900000000", "B", "CATCHECK10"))
                .thenReturn(created);

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"paymentMethod":"COD","receiverName":"A","receiverPhone":"0900000000","shippingAddress":"B","promoCode":"CATCHECK10"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.id().toString()));

        Mockito.verify(service).checkout(user.userId(), "COD", "A", "0900000000", "B", "CATCHECK10");
    }

    @Test
    void checkoutWithoutPromoCodePassesNullToService() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles();
        AdminApiMockMvc.authenticate(user);
        Order created = order("PENDING_PAYMENT", null);
        Mockito.when(service.checkout(user.userId(), "COD", "A", "0900000000", "B", null))
                .thenReturn(created);

        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("""
                        {"paymentMethod":"COD","receiverName":"A","receiverPhone":"0900000000","shippingAddress":"B"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(created.id().toString()));

        Mockito.verify(service).checkout(user.userId(), "COD", "A", "0900000000", "B", null);
    }

    @Test
    void myOrdersListsOrdersAndHidesInternalPaymentFields() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles();
        AdminApiMockMvc.authenticate(user);
        Mockito.when(service.myOrders(user.userId())).thenReturn(List.of(
                order("PENDING_PAYMENT", "https://pay.example.invalid/1"), order("CANCELLED", "https://pay.example.invalid/2")));

        mvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].checkoutUrl").value("https://pay.example.invalid/1"))
                .andExpect(jsonPath("$[0].paymentExpiresAt").exists())
                .andExpect(jsonPath("$[1].checkoutUrl").doesNotExist())
                .andExpect(jsonPath("$[1].cancelReason").value("BUYER"))
                .andExpect(jsonPath("$[0].payosOrderCode").doesNotExist())
                .andExpect(jsonPath("$[0].paymentReference").doesNotExist())
                .andExpect(jsonPath("$[0].refundNeeded").doesNotExist());
    }

    @Test
    void buyerCancelReturnsTheCancelledOrder() throws Exception {
        TestPrincipal user = TestPrincipal.withRoles();
        AdminApiMockMvc.authenticate(user);
        Order cancelled = order("CANCELLED", "https://pay.example.invalid/1");
        Mockito.when(service.cancelByBuyer(user.userId(), cancelled.id())).thenReturn(cancelled);

        mvc.perform(post("/api/v1/orders/{id}/cancel", cancelled.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void paymentMethodsOfferPayosOnlyWhenTheGatewayIsConfigured() throws Exception {
        PaymentGateway gateway = Mockito.mock(PaymentGateway.class);
        MockMvc real = AdminApiMockMvc.build(new ShopController(new ShopService(Mockito.mock(ShopRepository.class), gateway,
                null, Clock.systemUTC(), 3, "http://localhost:5173")));
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles());

        Mockito.when(gateway.enabled()).thenReturn(false);
        real.perform(get("/api/v1/shop/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"COD\"]", JsonCompareMode.STRICT));

        Mockito.when(gateway.enabled()).thenReturn(true);
        real.perform(get("/api/v1/shop/payment-methods"))
                .andExpect(status().isOk())
                .andExpect(content().json("[\"COD\",\"PAYOS\"]", JsonCompareMode.STRICT));
    }

    private static Order order(String status, String checkoutUrl) {
        return new Order(UUID.randomUUID(), "CC-ABC", status, "PAYOS", "A", "0900000000", "B", 100000, 0, 30000, 130000,
                List.of(), Instant.parse("2026-10-19T03:00:00Z"), 100001L, checkoutUrl,
                Instant.parse("2026-10-19T03:05:00Z"), null, "REF-SECRET", "CANCELLED".equals(status) ? "BUYER" : null, true);
    }
}
