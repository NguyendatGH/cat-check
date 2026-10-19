package com.catcheck.shop.infrastructure.persistence;

import com.catcheck.shared.testing.TestPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class ShopPayosSchemaIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);

    private static JdbcTemplate jdbc;
    private static UUID userId;

    @BeforeAll
    static void setUp() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration", "classpath:db/seed")
                .load().migrate();
        DriverManagerDataSource ds = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ds.setDriverClassName("org.postgresql.Driver");
        jdbc = new JdbcTemplate(ds);
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name) VALUES (?, 'Payos schema test') RETURNING id
                """, UUID.class, "payos-schema-" + UUID.randomUUID() + "@example.test");
    }

    private static void insertOrder(String method, Long payosCode, String cancelReason) {
        jdbc.update("""
                INSERT INTO shop_order (order_code, user_id, payment_method, receiver_name, receiver_phone,
                                        shipping_address, subtotal_vnd, total_vnd, payos_order_code, cancel_reason)
                VALUES (?, ?, ?, 'x', 'x', 'x', 1000, 1000, ?, ?)
                """, "T-" + UUID.randomUUID().toString().substring(0, 20), userId, method, payosCode, cancelReason);
    }

    @Test
    void payosOrderRequiresOrderCode() {
        assertThatThrownBy(() -> insertOrder("PAYOS", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatCode(() -> insertOrder("PAYOS", 7L, null)).doesNotThrowAnyException();
    }

    @Test
    void cancelReasonIsRestricted() {
        assertThatThrownBy(() -> insertOrder("COD", null, "FOO"))
                .isInstanceOf(DataIntegrityViolationException.class);
        for (String reason : new String[] {"PAYMENT_EXPIRED", "BUYER", "ADMIN"}) {
            assertThatCode(() -> insertOrder("COD", null, reason)).doesNotThrowAnyException();
        }
    }

    @Test
    void legacyMethodsStillAllowed() {
        assertThatCode(() -> insertOrder("MOMO", null, null)).doesNotThrowAnyException();
        assertThatCode(() -> insertOrder("BANK_TRANSFER", null, null)).doesNotThrowAnyException();
        assertThatThrownBy(() -> insertOrder("VNPAY", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sequenceStartsAt100000() {
        assertThat(jdbc.queryForObject("SELECT nextval('shop_order_payos_code_seq')", Long.class))
                .isEqualTo(100000L);
    }
}
