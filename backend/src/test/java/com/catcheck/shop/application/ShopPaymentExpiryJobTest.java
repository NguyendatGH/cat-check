package com.catcheck.shop.application;

import com.catcheck.shared.config.JdbcTemplateConfig;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.testing.TestPostgres;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Real Postgres + the real repository; orders are placed through ShopService at NOW, the job runs 6 minutes later. */
@Testcontainers
class ShopPaymentExpiryJobTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private static final Instant NOW = Instant.parse("2026-10-19T03:00:00Z");
    private static final Instant AFTER_WINDOW = NOW.plus(Duration.ofMinutes(6));
    private static final FakeGateway GATEWAY = new FakeGateway();

    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ShopRepository repository;
    private static ShopService service;

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
    void reset() {
        GATEWAY.reset();
        jdbc.update("DELETE FROM shop_order"); // the job sweeps every expired order, so each test starts empty
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

    private static UUID newProduct() {
        return jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Expiry test product', 'test', 100000, 5, 'PUBLISHED') RETURNING id
                """, UUID.class, "EXP-" + UUID.randomUUID());
    }

    private static Order checkout(UUID product, String method) {
        UUID user = jdbc.queryForObject("INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id",
                UUID.class, "exp-" + UUID.randomUUID() + "@example.invalid");
        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)", user, product);
        return service.checkout(user, method, "A", "0900000000", "Addr");
    }

    private static int stock(UUID product) {
        return jdbc.queryForObject("SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, product);
    }

    private static String status(Order order) {
        return jdbc.queryForObject("SELECT status FROM shop_order WHERE id = ?", String.class, order.id());
    }

    private static JobOutcome run(boolean dryRun) {
        ShopPaymentExpiryJob job = new ShopPaymentExpiryJob(null, repository, GATEWAY, 100, dryRun);
        return job.expireUnpaidOrders(new JobContext(UUID.randomUUID(), ShopPaymentExpiryJob.JOB_NAME, AFTER_WINDOW, dryRun));
    }

    @Test
    @DisplayName("Don PAYOS qua 5 phut chua tra: bi huy PAYMENT_EXPIRED, tra kho, dong link PayOS")
    void expiredUnpaidOrderIsCancelledAndRestocked() {
        UUID product = newProduct();
        Order order = checkout(product, "PAYOS");
        assertThat(stock(product)).isEqualTo(4);

        JobOutcome outcome = run(false);

        assertThat(outcome.status()).isEqualTo(JobRunStatus.SUCCESS);
        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        assertThat(status(order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM shop_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("PAYMENT_EXPIRED");
        assertThat(stock(product)).isEqualTo(5);
        assertThat(GATEWAY.cancelled).containsExactly(order.payosOrderCode() + ":PAYMENT_EXPIRED");
    }

    @Test
    @DisplayName("Da tra tien sat gio: markPaid voi late-check, khong huy, khong tra kho")
    void paidJustBeforeExpiryIsMarkedPaidNotCancelled() {
        UUID product = newProduct();
        Order order = checkout(product, "PAYOS");
        GATEWAY.paid.add(order.payosOrderCode());

        JobOutcome outcome = run(false);

        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        assertThat(status(order)).isEqualTo("PAID");
        assertThat(jdbc.queryForObject("SELECT payment_reference FROM shop_order WHERE id = ?", String.class, order.id()))
                .isEqualTo("late-check");
        assertThat(stock(product)).isEqualTo(4);
        assertThat(GATEWAY.cancelled).isEmpty();
    }

    @Test
    @DisplayName("Don COD cu hon 5 phut khong bao gio bi dong vao, PayOS khong bi goi")
    void codOrdersAreNeverTouched() {
        UUID product = newProduct();
        Order cod = checkout(product, "COD");

        JobOutcome outcome = run(false);

        assertThat(outcome.itemsProcessed()).isZero();
        assertThat(status(cod)).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(product)).isEqualTo(4);
        assertThat(GATEWAY.checked).isEmpty();
        assertThat(GATEWAY.cancelled).isEmpty();
    }

    @Test
    @DisplayName("Loi PayOS tren mot don khong chan don khac; don khong hoi duoc PayOS thi khong bi huy")
    void gatewayErrorOnOneOrderDoesNotStopOthers() {
        UUID product = newProduct();
        Order unknown = checkout(product, "PAYOS");
        Order expired = checkout(product, "PAYOS");
        GATEWAY.isPaidFailsFor.add(unknown.payosOrderCode());
        GATEWAY.cancelFails = true; // a failed link cancel is only logged

        JobOutcome outcome = run(false);

        assertThat(outcome.status()).isEqualTo(JobRunStatus.PARTIAL);
        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        assertThat(outcome.itemsFailed()).isEqualTo(1);
        assertThat(status(unknown)).as("khong chac da tra chua thi de lan sau").isEqualTo("PENDING_PAYMENT");
        assertThat(status(expired)).isEqualTo("CANCELLED");
        assertThat(stock(product)).isEqualTo(4);
    }

    @Test
    @DisplayName("dry_run chi dem, khong goi PayOS, khong doi don nao")
    void dryRunChangesNothing() {
        UUID product = newProduct();
        Order order = checkout(product, "PAYOS");

        JobOutcome outcome = run(true);

        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        assertThat(outcome.errorSummary()).contains("dry-run");
        assertThat(status(order)).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(product)).isEqualTo(4);
        assertThat(GATEWAY.checked).isEmpty();
        assertThat(GATEWAY.cancelled).isEmpty();
    }

    /** Hand-written fake: orders in {@code paid} report as paid, orders in {@code isPaidFailsFor} throw. */
    private static final class FakeGateway implements PaymentGateway {
        final Set<Long> paid = new HashSet<>();
        final Set<Long> isPaidFailsFor = new HashSet<>();
        final List<Long> checked = new ArrayList<>();
        final List<String> cancelled = new ArrayList<>();
        boolean cancelFails;

        void reset() {
            paid.clear();
            isPaidFailsFor.clear();
            checked.clear();
            cancelled.clear();
            cancelFails = false;
        }

        @Override public boolean enabled() { return true; }

        @Override public PaymentLink createLink(long code, long amountVnd, String description, Instant expiresAt,
                                                URI returnUrl, URI cancelUrl) {
            return new PaymentLink("link-" + code, "https://pay.example.invalid/" + code);
        }

        @Override public void cancelLink(long code, String reason) {
            if (cancelFails) throw new IllegalStateException("payos down");
            cancelled.add(code + ":" + reason);
        }

        @Override public boolean isPaid(long code) {
            checked.add(code);
            if (isPaidFailsFor.contains(code)) throw new IllegalStateException("payos down");
            return paid.contains(code);
        }

        @Override public VerifiedPayment verifyWebhook(String rawBody) { throw new UnsupportedOperationException(); }
    }
}
