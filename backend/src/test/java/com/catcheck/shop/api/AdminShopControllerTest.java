package com.catcheck.shop.api;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.TestPrincipal;
import com.catcheck.shop.application.AdminShopService;
import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.ExpiredPayment;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;
import com.catcheck.shop.domain.port.ShopRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminShopControllerTest {

    private static final UUID ID = UUID.fromString("01999999-0000-7000-8000-000000000001");
    private static final Instant LOADED_AT = Instant.parse("2026-10-01T03:04:05.678901Z");
    private static final String BODY = """
            {"name":"Khay cat CleanBox","description":"Khay thanh cao","priceVnd":480000,
             "stockQuantity":30,"status":"PUBLISHED"}
            """;

    private final FakeRepository repository = new FakeRepository();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        repository.product = product(10, LOADED_AT);
        mvc = AdminApiMockMvc.build(new AdminShopController(
                new AdminShopService(repository, Mockito.mock(AuditLogService.class))));
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("ADMIN_CATALOG"));
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    @Test
    @DisplayName("PUT thiếu If-Match ⇒ 428, không ghi")
    void missingIfMatchIsPreconditionRequired() throws Exception {
        mvc.perform(put("/api/v1/admin/shop/products/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isPreconditionRequired())
                .andExpect(jsonPath("$.errorCode").value("PRECONDITION_REQUIRED"));

        assertThat(repository.updates).isZero();
    }

    @Test
    @DisplayName("Checkout trừ kho sau khi mở form ⇒ If-Match cũ bị 412, tồn kho không bị ghi đè")
    void staleIfMatchAfterCheckoutIsRejected() throws Exception {
        String etagWhenFormOpened = frontendEtag(LOADED_AT, ID);
        repository.product = product(7, LOADED_AT.plusMillis(1500));

        mvc.perform(put("/api/v1/admin/shop/products/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("If-Match", etagWhenFormOpened))
                .andExpect(status().isPreconditionFailed())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_MODIFIED"));

        assertThat(repository.updates).isZero();
        assertThat(repository.product.stockQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("ETag frontend tự dựng W/\"<Date.parse(updatedAt)>-<id>\" từ JSON của GET ⇒ khớp, PUT 200")
    void etagRebuiltByFrontendFromGetResponseIsAccepted() throws Exception {
        String json = mvc.perform(get("/api/v1/admin/shop/products"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String updatedAt = JsonPath.read(json, "$[0].updatedAt");
        String id = JsonPath.read(json, "$[0].id");
        String etag = "W/\"" + Instant.parse(updatedAt).toEpochMilli() + "-" + id + "\"";
        assertThat(etag).isEqualTo("W/\"" + LOADED_AT.toEpochMilli() + "-" + ID + "\"");
        assertThat((String) JsonPath.read(json, "$[0].etag")).isEqualTo(etag);

        mvc.perform(put("/api/v1/admin/shop/products/{id}", ID).contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .header("If-Match", etag))
                .andExpect(status().isOk())
                .andExpect(header().exists("ETag"))
                .andExpect(jsonPath("$.stockQuantity").value(30));

        assertThat(repository.updates).isEqualTo(1);
    }

    private static String frontendEtag(Instant updatedAt, UUID id) {
        return "W/\"" + updatedAt.toEpochMilli() + "-" + id + "\"";
    }

    private static AdminProduct product(int stock, Instant updatedAt) {
        return new AdminProduct(ID, "CLEANBOX-TRAY", "Khay cat CleanBox", "Khay thanh cao", null,
                480000, null, stock, "PUBLISHED", updatedAt);
    }

    private static final class FakeRepository implements ShopRepository {
        AdminProduct product;
        int updates;

        @Override public List<AdminProduct> adminProducts() { return List.of(product); }
        @Override public Optional<AdminProduct> adminLockProduct(UUID id) {
            return Optional.ofNullable(product).filter(p -> p.id().equals(id));
        }
        @Override public Optional<AdminProduct> adminUpdate(UUID id, AdminProductDraft d) {
            updates++;
            product = new AdminProduct(id, product.sku(), d.name(), d.description(), d.imageUrl(), d.priceVnd(),
                    d.compareAtPriceVnd(), d.stockQuantity(), d.status(), product.updatedAt().plusSeconds(60));
            return Optional.of(product);
        }
        @Override public AdminProduct adminCreate(AdminProductDraft draft) { throw new UnsupportedOperationException(); }
        @Override public Optional<AdminProduct> adminSetStatus(UUID id, String status) { throw new UnsupportedOperationException(); }
        @Override public List<Product> products(String query, int limit) { throw new UnsupportedOperationException(); }
        @Override public Optional<Product> product(UUID productId) { throw new UnsupportedOperationException(); }
        @Override public List<CartLine> cart(UUID userId) { throw new UnsupportedOperationException(); }
        @Override public void setCartLine(UUID userId, UUID productId, int quantity) { throw new UnsupportedOperationException(); }
        @Override public void removeCartLine(UUID userId, UUID productId) { throw new UnsupportedOperationException(); }
        @Override public void clearCart(UUID userId) { throw new UnsupportedOperationException(); }
        @Override public Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address,
                                           String promoCode, Instant paymentExpiresAt, int maxOpenOrders) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<Order> order(UUID userId, UUID orderId) { throw new UnsupportedOperationException(); }
        @Override public List<Order> orders(UUID userId, int limit) { throw new UnsupportedOperationException(); }
        @Override public void attachPaymentLink(UUID orderId, String paymentLinkId, String checkoutUrl) { throw new UnsupportedOperationException(); }
        @Override public boolean transition(UUID orderId, String fromStatus, String toStatus) { throw new UnsupportedOperationException(); }
        @Override public boolean cancelAndRestock(UUID orderId, String expectedStatus, String cancelReason, Instant at, boolean moneyReceived) { throw new UnsupportedOperationException(); }
        @Override public MarkPaidResult markPaid(long payosOrderCode, long amountVnd, String reference, Instant paidAt) { throw new UnsupportedOperationException(); }
        @Override public Optional<String> paymentReference(long payosOrderCode) { throw new UnsupportedOperationException(); }
        @Override public List<ExpiredPayment> expiredPayosOrders(Instant now, int limit) { throw new UnsupportedOperationException(); }
        @Override public List<Order> adminOrders(String statusOrNull, int limit, int offset) { throw new UnsupportedOperationException(); }
        @Override public Optional<Order> adminOrder(UUID orderId) { throw new UnsupportedOperationException(); }
    }
}
