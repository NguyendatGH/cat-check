package com.catcheck.shop.api;

import com.catcheck.audit.api.AuditActorType;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.shared.config.JdbcTemplateConfig;
import com.catcheck.shared.testing.AdminApiMockMvc;
import com.catcheck.shared.testing.LogCapture;
import com.catcheck.shared.testing.TestPostgres;
import com.catcheck.shared.testing.TestPrincipal;
import com.catcheck.shop.application.AdminOrderService;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.infrastructure.persistence.JdbcShopRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real repository on Postgres so the transition, restock and refund flags are the production ones. */
@Testcontainers
class AdminOrderControllerTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private JdbcTemplate jdbc;
    private JdbcShopRepository repository;
    private AuditLogService audit;
    private PaymentGateway gateway;
    private MockMvc mvc;
    private UUID adminId;
    private UUID productId;

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
    }

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplateConfig().jdbcTemplate(new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())); // production Instant -> timestamptz handling
        repository = new JdbcShopRepository(jdbc);
        audit = Mockito.mock(AuditLogService.class);
        gateway = Mockito.mock(PaymentGateway.class);
        mvc = AdminApiMockMvc.build(new AdminOrderController(new AdminOrderService(repository, gateway, audit, Clock.systemUTC())));
        TestPrincipal admin = TestPrincipal.withRoles("ADMIN_CATALOG");
        adminId = admin.userId();
        AdminApiMockMvc.authenticate(admin);
        productId = jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Admin order test product', 'test', 100000, 5, 'PUBLISHED') RETURNING id
                """, UUID.class, "ADM-" + UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        AdminApiMockMvc.clear();
    }

    /** One line of the test product (stock 5 -> 4). */
    private Order order(String paymentMethod) {
        UUID user = jdbc.queryForObject("INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id",
                UUID.class, "adm-order-" + UUID.randomUUID() + "@example.invalid");
        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)", user, productId);
        return repository.createOrder(user, paymentMethod, "Nguyen A", "0900000000", "Addr", Instant.now().plusSeconds(300), 3);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, productId);
    }

    private String dbStatus(Order o) {
        return jdbc.queryForObject("SELECT status FROM shop_order WHERE id = ?", String.class, o.id());
    }

    private static String body(String status, String reason) {
        return reason == null ? "{\"status\":\"" + status + "\"}" : "{\"status\":\"" + status + "\",\"reason\":\"" + reason + "\"}";
    }

    private org.springframework.test.web.servlet.ResultActions move(Order o, String status, String reason) throws Exception {
        return mvc.perform(post("/api/v1/admin/shop/orders/{id}/status", o.id()).contentType(MediaType.APPLICATION_JSON).content(body(status, reason)));
    }

    @Test
    @DisplayName("Role USER ⇒ 403 cho list, chi tiet va doi trang thai")
    void userRoleIsForbidden() throws Exception {
        Order o = order("COD");
        AdminApiMockMvc.authenticate(TestPrincipal.withRoles("USER"));

        mvc.perform(get("/api/v1/admin/shop/orders")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_ROLE_REQUIRED"));
        mvc.perform(get("/api/v1/admin/shop/orders/{id}", o.id())).andExpect(status().isForbidden());
        move(o, "PACKING", null).andExpect(status().isForbidden());

        assertThat(dbStatus(o)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("COD PENDING_PAYMENT ⇒ PACKING, tra ve cac buoc tiep theo")
    void codOrderMovesToPacking() throws Exception {
        Order o = order("COD");

        move(o, "PACKING", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PACKING"))
                .andExpect(jsonPath("$.allowedNextStatuses[0]").value("SHIPPING"))
                .andExpect(jsonPath("$.allowedNextStatuses[1]").value("CANCELLED"))
                .andExpect(jsonPath("$.receiverName").value("Nguyen A"))
                .andExpect(jsonPath("$.checkoutUrl").doesNotExist());

        assertThat(dbStatus(o)).isEqualTo("PACKING");
    }

    @Test
    @DisplayName("PAYOS chua tra tien: admin danh dau PAID ⇒ 409, trang thai khong doi")
    void payosPendingCannotBeMarkedPaidByAdmin() throws Exception {
        Order o = order("PAYOS");

        move(o, "PAID", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_TRANSITION_INVALID"));
        move(o, "PACKING", null).andExpect(status().isConflict());

        assertThat(dbStatus(o)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("Huy khong co ly do (thieu hoac < 10 ky tu sau khi trim) ⇒ 400 REASON_REQUIRED, khong huy, khong tra kho")
    void cancelWithoutReasonIs400() throws Exception {
        Order o = order("COD");

        for (String reason : new String[]{null, "", "  Khach huy  "}) {
            move(o, "CANCELLED", reason).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("REASON_REQUIRED"));
        }

        assertThat(dbStatus(o)).isEqualTo("PENDING_PAYMENT");
        assertThat(stock()).isEqualTo(4);
    }

    @Test
    @DisplayName("Admin huy don COD co ly do ⇒ CANCELLED/ADMIN, tra kho; huy lan 2 ⇒ 409")
    void adminCancelRestocks() throws Exception {
        Order o = order("COD");
        assertThat(stock()).isEqualTo(4);

        move(o, "CANCELLED", "Khach goi bao huy").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("ADMIN"))
                .andExpect(jsonPath("$.allowedNextStatuses").isEmpty());
        move(o, "CANCELLED", "Khach goi bao huy").andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("ORDER_TRANSITION_INVALID"));

        assertThat(stock()).isEqualTo(5);
    }

    @Test
    @DisplayName("Admin huy don PAYOS chua tra ⇒ dong link PayOS (ADMIN_CANCELLED) truoc khi huy; COD va don da tra thi khong goi PayOS")
    void adminCancelOfPendingPayosClosesTheLink() throws Exception {
        Order pending = order("PAYOS");
        Order paid = order("PAYOS");
        jdbc.update("UPDATE shop_order SET status = 'PAID', paid_at = now(), payment_reference = 'REF1' WHERE id = ?", paid.id());
        Order cod = order("COD");

        move(pending, "CANCELLED", "Khach goi bao huy don").andExpect(status().isOk());
        move(paid, "CANCELLED", "Het hang that su, hoan tien").andExpect(status().isOk());
        move(cod, "CANCELLED", "Khach doi y khong mua nua").andExpect(status().isOk());

        Mockito.verify(gateway).cancelLink(pending.payosOrderCode(), "ADMIN_CANCELLED");
        Mockito.verifyNoMoreInteractions(gateway);
    }

    @Test
    @DisplayName("PayOS loi khi dong link: chi ghi log (khong lo noi dung loi), don van huy va tra kho")
    void adminCancelSucceedsWhenLinkCancelFails() throws Exception {
        Order pending = order("PAYOS");
        String pii = "payer Nguyen Van A, account 0123456789";
        Mockito.doThrow(new IllegalStateException(pii)).when(gateway).cancelLink(Mockito.anyLong(), Mockito.anyString());

        String logged = LogCapture.text(LogCapture.capture(AdminOrderService.class, () ->
                move(pending, "CANCELLED", "Khach goi bao huy don").andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("CANCELLED"))));

        assertThat(stock()).isEqualTo(5);
        assertThat(logged).contains("IllegalStateException").doesNotContain(pii);
    }

    @Test
    @DisplayName("Huy don PAYOS da tra tien ⇒ refundNeeded=true trong chi tiet; huy don COD thi false")
    void refundNeededIsExposed() throws Exception {
        Order paid = order("PAYOS");
        jdbc.update("UPDATE shop_order SET status = 'PAID', paid_at = now(), payment_reference = 'REF1' WHERE id = ?", paid.id());
        Order cod = order("COD");

        mvc.perform(get("/api/v1/admin/shop/orders/{id}", paid.id())).andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentReference").value("REF1"))
                .andExpect(jsonPath("$.payosOrderCode").value(paid.payosOrderCode()))
                .andExpect(jsonPath("$.paidAt").exists())
                .andExpect(jsonPath("$.checkoutUrl").doesNotExist())
                .andExpect(jsonPath("$.refundNeeded").value(false))
                .andExpect(jsonPath("$.allowedNextStatuses[0]").value("PACKING"));
        move(paid, "CANCELLED", "Het hang that su, hoan tien").andExpect(status().isOk());
        move(cod, "CANCELLED", "Khach doi y khong mua nua").andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/shop/orders/{id}", paid.id()))
                .andExpect(jsonPath("$.refundNeeded").value(true));
        mvc.perform(get("/api/v1/admin/shop/orders/{id}", cod.id()))
                .andExpect(jsonPath("$.refundNeeded").value(false))
                .andExpect(jsonPath("$.payosOrderCode").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("Ghi audit: doi trang thai chi co orderId/from/to; huy them reason (da trim)")
    void auditEventWritten() throws Exception {
        Order o = order("COD");

        move(o, "PACKING", null).andExpect(status().isOk());
        move(o, "CANCELLED", "  Khach goi bao huy don  ").andExpect(status().isOk());

        ArgumentCaptor<AuditEvent> events = ArgumentCaptor.forClass(AuditEvent.class);
        Mockito.verify(audit, Mockito.times(2)).record(events.capture());
        AuditEvent moved = events.getAllValues().get(0);
        AuditEvent cancelled = events.getAllValues().get(1);
        assertThat(moved.action()).isEqualTo("SHOP_ORDER_STATUS_CHANGED");
        assertThat(moved.metadata()).containsOnly(
                java.util.Map.entry("orderId", o.id().toString()),
                java.util.Map.entry("from", "PENDING_PAYMENT"),
                java.util.Map.entry("to", "PACKING"));
        assertThat(moved.actor().type()).isEqualTo(AuditActorType.ADMIN);
        assertThat(moved.actor().userId()).isEqualTo(adminId);
        assertThat(cancelled.action()).isEqualTo("SHOP_ORDER_CANCELLED");
        assertThat(cancelled.metadata()).containsOnly(
                java.util.Map.entry("orderId", o.id().toString()),
                java.util.Map.entry("from", "PACKING"),
                java.util.Map.entry("to", "CANCELLED"),
                java.util.Map.entry("reason", "Khach goi bao huy don"));
    }

    @Test
    @DisplayName("Danh sach loc theo status, page am ⇒ 400, don khong co ⇒ 404")
    void listFiltersByStatusAndRejectsNegativePage() throws Exception {
        Order o = order("COD");
        jdbc.update("UPDATE shop_order SET status = 'SHIPPING' WHERE id = ?", o.id());

        mvc.perform(get("/api/v1/admin/shop/orders").param("status", "SHIPPING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(o.id().toString()))
                .andExpect(jsonPath("$[0].allowedNextStatuses[0]").value("DELIVERED"))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mvc.perform(get("/api/v1/admin/shop/orders").param("status", "SHIPPING").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0]").doesNotExist());
        mvc.perform(get("/api/v1/admin/shop/orders").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/shop/orders/{id}", UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }
}
