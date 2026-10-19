package com.catcheck.shop.application;

import com.catcheck.shared.config.JdbcTemplateConfig;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.testing.TestPostgres;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import com.catcheck.shop.infrastructure.persistence.JdbcShopRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Wires a real Spring context (transaction proxy included) so the checkout rollback is the production one. */
@Testcontainers
class ShopServicePayosTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    private static final FakeGateway GATEWAY = new FakeGateway();

    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ShopService service;
    private static ShopRepository repository;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        context = new AnnotationConfigApplicationContext();
        context.registerBean(DataSource.class, () -> ds);
        context.register(TestConfig.class);
        context.refresh();
        jdbc = context.getBean(JdbcTemplate.class);
        repository = context.getBean(ShopRepository.class);
        service = context.getBean(ShopService.class);
    }

    @AfterAll
    static void tearDown() {
        context.close();
    }

    @BeforeEach
    void resetGateway() {
        GATEWAY.reset();
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplateConfig().jdbcTemplate(ds); }
        @Bean ShopRepository shopRepository(JdbcTemplate jdbc) { return new JdbcShopRepository(jdbc); }
        @Bean ShopService shopService(ShopRepository repository) {
            return new ShopService(repository, GATEWAY, new PayOsProperties(null, null, null, null, null, null),
                    Clock.fixed(NOW, ZoneOffset.UTC), 3, "http://localhost:5173");
        }
    }

    private static UUID newUser() {
        return jdbc.queryForObject("INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id",
                UUID.class, "svc-" + UUID.randomUUID() + "@example.invalid");
    }

    /** Price 100000, stock 5, so total = 100000 + 30000 shipping = 130000. */
    private static UUID newProduct() {
        return jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Service test product', 'test', 100000, 5, 'PUBLISHED') RETURNING id
                """, UUID.class, "SVC-" + UUID.randomUUID());
    }

    private static void addToCart(UUID user, UUID product) {
        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)", user, product);
    }

    private static Order checkout(UUID user, UUID product, String method) {
        addToCart(user, product);
        return service.checkout(user, method, "A", "0900000000", "Addr");
    }

    private static int stock(UUID product) {
        return jdbc.queryForObject("SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, product);
    }

    private static int orderCount(UUID user) {
        return jdbc.queryForObject("SELECT count(*) FROM shop_order WHERE user_id = ?", Integer.class, user);
    }

    private static void assertBusinessError(Throwable e, ShopErrorCode code) {
        assertThat(e).isInstanceOf(BusinessRuleException.class);
        assertThat(((BusinessRuleException) e).errorCode()).isEqualTo(code);
    }

    @Test
    @DisplayName("PAYOS: tao link voi dung so tien, mo ta CC<ma>, het han sau 5 phut, URL tro ve don")
    void payosCheckoutCreatesLinkWithFiveMinuteExpiry() {
        UUID user = newUser();
        UUID product = newProduct();

        Order order = checkout(user, product, "PAYOS");

        assertThat(GATEWAY.created).hasSize(1);
        FakeGateway.Created link = GATEWAY.created.getFirst();
        assertThat(link.code()).isEqualTo(order.payosOrderCode());
        assertThat(link.amountVnd()).isEqualTo(order.totalVnd()).isEqualTo(130000);
        assertThat(link.description()).isEqualTo("CC" + order.payosOrderCode());
        assertThat(link.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(link.returnUrl().toString()).isEqualTo("http://localhost:5173/orders/" + order.id() + "?payment=return");
        assertThat(link.cancelUrl().toString()).isEqualTo("http://localhost:5173/orders/" + order.id() + "?payment=cancel");
        assertThat(order.paymentExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(order.checkoutUrl()).isEqualTo("https://pay.example.invalid/" + order.payosOrderCode());
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test
    @DisplayName("PayOS loi => PAYMENT_UNAVAILABLE, khong co don moi, kho va gio hang nguyen ven")
    void gatewayFailureRollsBackOrderStockAndCart() {
        UUID user = newUser();
        UUID product = newProduct();
        addToCart(user, product);
        GATEWAY.createFailure = new IllegalStateException("payos down");

        assertThatThrownBy(() -> service.checkout(user, "PAYOS", "A", "0900000000", "Addr"))
                .satisfies(e -> assertBusinessError(e, ShopErrorCode.PAYMENT_UNAVAILABLE));

        assertThat(orderCount(user)).isZero();
        assertThat(stock(product)).isEqualTo(5);
        assertThat(repository.cart(user)).hasSize(1);
    }

    @Test
    @DisplayName("Cong thanh toan tat => PAYOS bi tu choi truoc khi ghi gi; COD van dat duoc")
    void disabledGatewayRejectsPayosButCodWorks() {
        UUID user = newUser();
        UUID product = newProduct();
        addToCart(user, product);
        GATEWAY.enabled = false;

        assertThatThrownBy(() -> service.checkout(user, "PAYOS", "A", "0900000000", "Addr"))
                .satisfies(e -> assertBusinessError(e, ShopErrorCode.PAYMENT_UNAVAILABLE));
        assertThat(GATEWAY.created).isEmpty();
        assertThat(orderCount(user)).isZero();
        assertThat(repository.cart(user)).hasSize(1);

        Order cod = service.checkout(user, "COD", "A", "0900000000", "Addr");
        assertThat(cod.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(cod.checkoutUrl()).isNull();
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test
    @DisplayName("Nguoi mua huy don PAYOS: tra kho, huy link PayOS, ly do BUYER")
    void buyerCancelRestocksAndCancelsLink() {
        UUID user = newUser();
        UUID product = newProduct();
        Order order = checkout(user, product, "PAYOS");
        assertThat(stock(product)).isEqualTo(4);

        Order cancelled = service.cancelByBuyer(user, order.id());

        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("BUYER");
        assertThat(stock(product)).isEqualTo(5);
        assertThat(GATEWAY.cancelled).containsExactly(order.payosOrderCode() + ":BUYER_CANCELLED");
    }

    @Test
    @DisplayName("Huy link PayOS loi chi bi ghi log, don van duoc huy va tra kho")
    void buyerCancelSucceedsWhenLinkCancelFails() {
        UUID user = newUser();
        UUID product = newProduct();
        Order order = checkout(user, product, "PAYOS");
        GATEWAY.cancelFailure = new IllegalStateException("payos down");

        assertThat(service.cancelByBuyer(user, order.id()).status()).isEqualTo("CANCELLED");
        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    @DisplayName("Don da tra tien khong huy duoc: ORDER_NOT_CANCELLABLE, kho va link khong dong den")
    void buyerCannotCancelPaidOrder() {
        UUID user = newUser();
        UUID product = newProduct();
        Order order = checkout(user, product, "PAYOS");
        repository.markPaid(order.payosOrderCode(), order.totalVnd(), "REF", NOW);

        assertThatThrownBy(() -> service.cancelByBuyer(user, order.id()))
                .satisfies(e -> assertBusinessError(e, ShopErrorCode.ORDER_NOT_CANCELLABLE));

        assertThat(service.order(user, order.id()).status()).isEqualTo("PAID");
        assertThat(stock(product)).isEqualTo(4);
        assertThat(GATEWAY.cancelled).isEmpty();
    }

    @Test
    @DisplayName("Khong huy duoc don cua nguoi khac: ORDER_NOT_FOUND")
    void buyerCannotCancelSomeoneElsesOrder() {
        UUID owner = newUser();
        Order order = checkout(owner, newProduct(), "PAYOS");

        assertThatThrownBy(() -> service.cancelByBuyer(newUser(), order.id()))
                .isInstanceOf(NotFoundException.class)
                .satisfies(e -> assertThat(((NotFoundException) e).errorCode()).isEqualTo(ShopErrorCode.ORDER_NOT_FOUND));

        assertThat(service.order(owner, order.id()).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(GATEWAY.cancelled).isEmpty();
    }

    @Test
    @DisplayName("Don cua toi chi gom don cua nguoi dung, moi nhat truoc")
    void myOrdersListsOnlyOwnOrdersNewestFirst() {
        UUID user = newUser();
        UUID product = newProduct();
        Order first = checkout(user, product, "COD");
        Order second = checkout(user, product, "COD");
        checkout(newUser(), product, "COD");

        assertThat(service.myOrders(user)).extracting(Order::id).containsExactly(second.id(), first.id());
    }

    /** Records every call; can be told to throw or to report itself disabled. */
    private static final class FakeGateway implements PaymentGateway {
        record Created(long code, long amountVnd, String description, Instant expiresAt, URI returnUrl, URI cancelUrl) { }

        boolean enabled;
        RuntimeException createFailure;
        RuntimeException cancelFailure;
        final List<Created> created = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();

        void reset() {
            enabled = true;
            createFailure = null;
            cancelFailure = null;
            created.clear();
            cancelled.clear();
        }

        @Override public boolean enabled() { return enabled; }

        @Override public PaymentLink createLink(long code, long amountVnd, String description, Instant expiresAt,
                                                URI returnUrl, URI cancelUrl) {
            if (createFailure != null) throw createFailure;
            created.add(new Created(code, amountVnd, description, expiresAt, returnUrl, cancelUrl));
            return new PaymentLink("link-" + code, "https://pay.example.invalid/" + code);
        }

        @Override public void cancelLink(long code, String reason) {
            if (cancelFailure != null) throw cancelFailure;
            cancelled.add(code + ":" + reason);
        }

        @Override public long amountPaid(long code) { throw new UnsupportedOperationException(); }

        @Override public VerifiedPayment verifyWebhook(String rawBody) {
            throw new UnsupportedOperationException();
        }
    }
}
