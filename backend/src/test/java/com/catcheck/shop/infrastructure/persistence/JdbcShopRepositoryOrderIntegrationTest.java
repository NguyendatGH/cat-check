package com.catcheck.shop.infrastructure.persistence;

import com.catcheck.shared.config.JdbcTemplateConfig;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.testing.TestPostgres;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.ExpiredPayment;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.Order;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcShopRepositoryOrderIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private static JdbcTemplate jdbc;
    private static JdbcShopRepository repository;
    private static TransactionTemplate tx;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplateConfig().jdbcTemplate(ds); // same Instant -> timestamptz handling as production
        repository = new JdbcShopRepository(jdbc);
        tx = new TransactionTemplate(new DataSourceTransactionManager(ds)); // plain new has no @Transactional proxy
    }

    private static UUID newUser() {
        return jdbc.queryForObject("INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id",
                UUID.class, "order-" + UUID.randomUUID() + "@example.invalid");
    }

    /** Price 100000, so total = 100000 + 30000 shipping = 130000. */
    private static UUID newProduct() {
        return jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Order test product', 'test', 100000, 5, 'PUBLISHED') RETURNING id
                """, UUID.class, "ORD-" + UUID.randomUUID());
    }

    private static Order place(UUID user, UUID product, String method, Instant expiresAt, int cap) {
        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1) ON CONFLICT DO NOTHING",
                user, product);
        return tx.execute(s -> repository.createOrder(user, method, "A", "0900000000", "Addr", expiresAt, cap));
    }

    private static Order placePayos(UUID user, UUID product) {
        return place(user, product, "PAYOS", Instant.now().plus(5, ChronoUnit.MINUTES), 3);
    }

    private static MarkPaidResult pay(long code, long amount, String ref) {
        return tx.execute(s -> repository.markPaid(code, amount, ref, Instant.now()));
    }

    private static boolean cancel(UUID orderId, String expected) {
        return Boolean.TRUE.equals(tx.execute(s ->
                repository.cancelAndRestock(orderId, expected, "BUYER", Instant.now())));
    }

    private static int stock(UUID product) {
        return jdbc.queryForObject("SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, product);
    }

    private static Order reload(UUID user, UUID orderId) {
        return repository.order(user, orderId).orElseThrow();
    }

    @Test
    @DisplayName("PAYOS: co payos_order_code >= 100000, co han thanh toan, tru kho, gio hang rong")
    void payosCheckoutAssignsOrderCodeAndExpiry() {
        UUID user = newUser();
        UUID product = newProduct();
        Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);

        Order order = place(user, product, "PAYOS", expiresAt, 3);

        assertThat(order.payosOrderCode()).isGreaterThanOrEqualTo(100000L);
        assertThat(order.paymentExpiresAt()).isEqualTo(expiresAt);
        assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(stock(product)).isEqualTo(4);
        assertThat(repository.cart(user)).isEmpty();
    }

    @Test
    @DisplayName("COD: khong co payos_order_code, khong co han thanh toan")
    void codCheckoutHasNoOrderCodeOrExpiry() {
        Order order = place(newUser(), newProduct(), "COD", null, 3);

        assertThat(order.payosOrderCode()).isNull();
        assertThat(order.paymentExpiresAt()).isNull();
        assertThat(order.refundNeeded()).isFalse();
    }

    @Test
    @DisplayName("Don mo thu 4 bi tu choi; sau khi mot don chuyen PACKING thi dat tiep duoc")
    void fourthOpenOrderIsRejected() {
        UUID user = newUser();
        UUID product = newProduct();
        Order first = placePayos(user, product);
        placePayos(user, product);
        placePayos(user, product);

        assertThatThrownBy(() -> placePayos(user, product))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> {
                    ConflictException conflict = (ConflictException) e;
                    assertThat(conflict.errorCode()).isEqualTo(ShopErrorCode.ORDER_LIMIT_REACHED);
                    assertThat(conflict.args()).as("limit reaches the {0} message argument").containsExactly(3);
                });
        assertThat(stock(product)).as("don bi tu choi khong tru kho").isEqualTo(2);
        assertThat(repository.cart(user)).as("gio hang con nguyen").hasSize(1);

        assertThat(repository.transition(first.id(), "PAID", "PACKING")).as("sai trang thai nguon").isFalse();
        assertThat(repository.transition(first.id(), "PENDING_PAYMENT", "PACKING")).isTrue();
        assertThat(placePayos(user, product).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("Huy tra kho; goi lan hai voi trang thai cu => false, kho khong cong lan hai")
    void cancelAndRestockReturnsStock() {
        UUID user = newUser();
        UUID product = newProduct();
        Order order = placePayos(user, product);
        assertThat(stock(product)).isEqualTo(4);

        assertThat(cancel(order.id(), "PENDING_PAYMENT")).isTrue();
        assertThat(stock(product)).isEqualTo(5);
        Order cancelled = reload(user, order.id());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
        assertThat(cancelled.cancelReason()).isEqualTo("BUYER");

        assertThat(cancel(order.id(), "PENDING_PAYMENT")).isFalse();
        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    @DisplayName("Hai lan huy dong thoi => dung mot lan tra kho")
    void concurrentCancelRestocksOnce() throws Exception {
        UUID user = newUser();
        UUID product = newProduct();
        Order order = placePayos(user, product);

        List<Boolean> results = race(() -> cancel(order.id(), "PENDING_PAYMENT"));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(stock(product)).isEqualTo(5);
    }

    @Test
    @DisplayName("markPaid idempotent: lan 1 PAID, lan 2 ALREADY_PAID, paid_at khong doi")
    void markPaidIsIdempotent() {
        UUID user = newUser();
        Order order = placePayos(user, newProduct());

        assertThat(pay(order.payosOrderCode(), order.totalVnd(), "REF1")).isEqualTo(MarkPaidResult.PAID);
        Order paid = reload(user, order.id());
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.paymentReference()).isEqualTo("REF1");

        assertThat(pay(order.payosOrderCode(), order.totalVnd(), "REF2")).isEqualTo(MarkPaidResult.ALREADY_PAID);
        Order again = reload(user, order.id());
        assertThat(again.paidAt()).isEqualTo(paid.paidAt());
        assertThat(again.paymentReference()).isEqualTo("REF1");
    }

    @Test
    @DisplayName("Hai webhook dong thoi => dung mot PAID")
    void concurrentMarkPaidPaysOnce() throws Exception {
        Order order = placePayos(newUser(), newProduct());

        List<MarkPaidResult> results = race(() -> pay(order.payosOrderCode(), order.totalVnd(), "REF"));

        assertThat(results).containsExactlyInAnyOrder(MarkPaidResult.PAID, MarkPaidResult.ALREADY_PAID);
    }

    @Test
    @DisplayName("So tien khac total_vnd => AMOUNT_MISMATCH, don van PENDING_PAYMENT")
    void markPaidAmountMismatch() {
        UUID user = newUser();
        Order order = placePayos(user, newProduct());

        assertThat(pay(order.payosOrderCode(), order.totalVnd() - 1, "REF")).isEqualTo(MarkPaidResult.AMOUNT_MISMATCH);

        Order after = reload(user, order.id());
        assertThat(after.status()).isEqualTo("PENDING_PAYMENT");
        assertThat(after.paidAt()).isNull();
    }

    @Test
    @DisplayName("Tra tien sau khi huy => PAID_AFTER_CANCEL, refundNeeded, van CANCELLED; lap lai khong ghi de paid_at")
    void markPaidAfterCancelFlagsRefund() {
        UUID user = newUser();
        Order order = placePayos(user, newProduct());
        cancel(order.id(), "PENDING_PAYMENT");

        assertThat(pay(order.payosOrderCode(), order.totalVnd(), "LATE")).isEqualTo(MarkPaidResult.PAID_AFTER_CANCEL);
        Order flagged = reload(user, order.id());
        assertThat(flagged.status()).isEqualTo("CANCELLED");
        assertThat(flagged.refundNeeded()).isTrue();
        assertThat(flagged.paidAt()).isNotNull();
        assertThat(flagged.paymentReference()).isEqualTo("LATE");

        assertThat(pay(order.payosOrderCode(), order.totalVnd(), "LATE2")).isEqualTo(MarkPaidResult.ALREADY_PAID);
        Order again = reload(user, order.id());
        assertThat(again.paidAt()).isEqualTo(flagged.paidAt());
        assertThat(again.paymentReference()).isEqualTo("LATE");
    }

    @Test
    @DisplayName("Ma don khong ton tai => UNKNOWN_ORDER")
    void markPaidUnknownOrder() {
        assertThat(pay(999_999_999L, 1, "REF")).isEqualTo(MarkPaidResult.UNKNOWN_ORDER);
    }

    @Test
    @DisplayName("Chi tra don PAYOS dang PENDING_PAYMENT da het han; bo qua COD, chua het han, da tra")
    void expiredQueryIgnoresCodAndFuture() {
        UUID user = newUser();
        UUID product = newProduct();
        Instant past = Instant.now().minus(1, ChronoUnit.MINUTES);
        Order expired = place(user, product, "PAYOS", past, 9);
        Order future = place(user, product, "PAYOS", Instant.now().plus(5, ChronoUnit.MINUTES), 9);
        Order cod = place(user, product, "COD", null, 9);
        Order paid = place(user, product, "PAYOS", past, 9);
        pay(paid.payosOrderCode(), paid.totalVnd(), "REF");

        List<ExpiredPayment> found = repository.expiredPayosOrders(Instant.now(), 100);

        assertThat(found).extracting(ExpiredPayment::orderId)
                .contains(expired.id())
                .doesNotContain(future.id(), cod.id(), paid.id());
        assertThat(found).filteredOn(e -> e.orderId().equals(expired.id()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.payosOrderCode()).isEqualTo(expired.payosOrderCode());
                    assertThat(e.totalVnd()).isEqualTo(expired.totalVnd());
                });
    }

    @Test
    @DisplayName("orders(userB) khong thay don cua userA; moi nhat truoc")
    void ordersAreUserScoped() {
        UUID a = newUser();
        UUID b = newUser();
        UUID product = newProduct();
        Order first = place(a, product, "COD", null, 9);
        Order second = place(a, product, "COD", null, 9);

        assertThat(repository.orders(b, 10)).isEmpty();
        assertThat(repository.orders(a, 10)).extracting(Order::id).containsExactly(second.id(), first.id());
        assertThat(repository.orders(a, 1)).hasSize(1);
    }

    @Test
    @DisplayName("attachPaymentLink luu link; admin doc duoc don bat ky user va loc theo trang thai")
    void attachPaymentLinkAndAdminQueries() {
        UUID user = newUser();
        Order order = placePayos(user, newProduct());

        repository.attachPaymentLink(order.id(), "plink-1", "https://pay.example.invalid/x");

        Order seen = repository.adminOrder(order.id()).orElseThrow();
        assertThat(seen.checkoutUrl()).isEqualTo("https://pay.example.invalid/x");
        assertThat(seen.lines()).hasSize(1);
        assertThat(repository.adminOrder(UUID.randomUUID())).isEmpty();
        assertThat(repository.adminOrders("PENDING_PAYMENT", 1000, 0)).extracting(Order::id).contains(order.id());
        assertThat(repository.adminOrders("DELIVERED", 1000, 0)).extracting(Order::id).doesNotContain(order.id());
        assertThat(repository.adminOrders(null, 1000, 0)).extracting(Order::id).contains(order.id());
    }

    /** Run the same action on two threads released together; the row lock must serialize them. */
    private static <T> List<T> race(Callable<T> action) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<T> task = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return action.call();
            };
            var a = pool.submit(task);
            var b = pool.submit(task);
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }
}
