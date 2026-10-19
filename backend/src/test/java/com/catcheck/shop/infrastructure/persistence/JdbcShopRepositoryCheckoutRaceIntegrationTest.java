package com.catcheck.shop.infrastructure.persistence;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.security.AdminApiErrorCode;
import com.catcheck.shared.testing.TestPostgres;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.api.dto.AdminProductRequest;
import com.catcheck.shop.application.AdminShopService;
import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.Order;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JdbcShopRepositoryCheckoutRaceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private JdbcTemplate jdbc;
    private JdbcShopRepository repository;
    private TransactionTemplate txTemplate;

    private UUID userId;
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
        DataSource dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcShopRepository(jdbc);
        txTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Shop race test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "shop-race-" + UUID.randomUUID() + "@example.test");

        productId = jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Race test product', 'test', 100000, 5, 'PUBLISHED')
                RETURNING id
                """, UUID.class, "RACE-" + UUID.randomUUID());

        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)",
                userId, productId);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shop_order_line WHERE product_id = ?", productId);
        jdbc.update("DELETE FROM shop_order WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM shop_cart_line WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM shop_product WHERE id = ?", productId);
        jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
    }

    @Test
    @DisplayName("Double-click dat hang cung gio hang => dung mot don, khong tru kho hai lan")
    void concurrentCheckoutOfSameCartCreatesExactlyOneOrder() throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<CheckoutOutcome> attempt = () -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    Order order = txTemplate.execute(status ->
                            repository.createOrder(userId, "COD", "Nguyen Van A", "0900000000", "Dia chi test", null, 99));
                    return CheckoutOutcome.success(order);
                } catch (ConflictException ex) {
                    return CheckoutOutcome.failure(ex);
                }
            };

            Future<CheckoutOutcome> first = pool.submit(attempt);
            Future<CheckoutOutcome> second = pool.submit(attempt);

            CheckoutOutcome a = first.get(15, TimeUnit.SECONDS);
            CheckoutOutcome b = second.get(15, TimeUnit.SECONDS);

            List<CheckoutOutcome> outcomes = List.of(a, b);
            long successCount = outcomes.stream().filter(CheckoutOutcome::isSuccess).count();
            long cartEmptyCount = outcomes.stream()
                    .filter(o -> !o.isSuccess() && o.error().errorCode() == ShopErrorCode.CART_EMPTY)
                    .count();

            assertThat(successCount)
                    .as("dung mot trong hai request duoc tao don — double-submit khong duoc tao hai don")
                    .isEqualTo(1);
            assertThat(cartEmptyCount)
                    .as("request thua phai thay gio hang da rong (CART_EMPTY), khong duoc tao don thu hai")
                    .isEqualTo(1);

            Integer orderCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shop_order WHERE user_id = ?", Integer.class, userId);
            assertThat(orderCount).as("chi mot dong shop_order duoc tao").isEqualTo(1);

            Integer stockQuantity = jdbc.queryForObject(
                    "SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, productId);
            assertThat(stockQuantity).as("kho chi tru DUNG MOT LAN, khong phai hai lan").isEqualTo(4);

            Integer cartLineCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM shop_cart_line WHERE user_id = ?", Integer.class, userId);
            assertThat(cartLineCount).as("gio hang phai rong sau khi dat hang thanh cong").isZero();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("San pham da ARCHIVED sau khi vao gio => dat hang bi tu choi, khong tao don, khong tru kho")
    void checkoutRejectsProductUnpublishedAfterItWasAddedToCart() {
        jdbc.update("UPDATE shop_product SET status = 'ARCHIVED' WHERE id = ?", productId);

        assertThatThrownBy(() -> txTemplate.execute(status ->
                repository.createOrder(userId, "COD", "Nguyen Van A", "0900000000", "Dia chi test", null, 99)))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).errorCode())
                .isEqualTo(ShopErrorCode.STOCK_UNAVAILABLE);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM shop_order WHERE user_id = ?", Integer.class, userId))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT stock_quantity FROM shop_product WHERE id = ?", Integer.class, productId))
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Ba don lien tiep trong vai ms => ba order_code khac nhau, khong DuplicateKey")
    void ordersPlacedWithinTheSameMomentGetDistinctOrderCodes() {
        List<String> codes = txTemplate.execute(status -> {
            List<String> placed = new java.util.ArrayList<>();
            for (int i = 0; i < 3; i++) {
                if (i > 0) {
                    jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)",
                            userId, productId);
                }
                placed.add(repository.createOrder(userId, "COD", "Nguyen Van A", "0900000000", "Dia chi test", null, 99).orderCode());
            }
            return placed;
        });

        assertThat(codes).hasSize(3).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Admin mo form, khach dat hang tru kho, admin luu voi If-Match cu => 412, kho khong bi ghi de")
    void adminSaveWithStaleEtagAfterCheckoutDoesNotOverwriteStock() {
        AdminShopService admin = new AdminShopService(repository, Mockito.mock(AuditLogService.class));
        String etagWhenFormOpened = AdminShopService.etagOf(adminProduct());
        AdminProductRequest form = new AdminProductRequest(null, "Race test product", "test", null,
                100000L, null, 5, "PUBLISHED");

        txTemplate.execute(status -> repository.createOrder(userId, "COD", "Nguyen Van A", "0900000000", "Dia chi test", null, 99));

        assertThatThrownBy(() -> txTemplate.execute(status ->
                admin.update(productId, form, etagWhenFormOpened, UUID.randomUUID(), "ADMIN_CATALOG")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(AdminApiErrorCode.RESOURCE_MODIFIED);
        assertThat(adminProduct().stockQuantity()).as("kho van la 5 - 1, khong bi tra ve 5").isEqualTo(4);

        String fresh = AdminShopService.etagOf(adminProduct());
        txTemplate.execute(status -> admin.update(productId, form, fresh, UUID.randomUUID(), "ADMIN_CATALOG"));
        assertThat(adminProduct().stockQuantity()).isEqualTo(5);
    }

    private AdminProduct adminProduct() {
        return repository.adminProducts().stream().filter(p -> p.id().equals(productId)).findFirst().orElseThrow();
    }

    private record CheckoutOutcome(Order order, ConflictException error) {
        static CheckoutOutcome success(Order order) {
            return new CheckoutOutcome(order, null);
        }

        static CheckoutOutcome failure(ConflictException error) {
            return new CheckoutOutcome(null, error);
        }

        boolean isSuccess() {
            return order != null;
        }
    }
}
