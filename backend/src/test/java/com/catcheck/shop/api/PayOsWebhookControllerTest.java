package com.catcheck.shop.api;

import com.catcheck.shared.config.JdbcTemplateConfig;
import com.catcheck.shared.config.SecurityConfig;
import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.AdminAccessGate;
import com.catcheck.shared.testing.TestPostgres;
import com.catcheck.shop.application.PaymentWebhookService;
import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import com.catcheck.shop.infrastructure.persistence.JdbcShopRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real filter chain + real service + real DB; only PayOS (signature check) is faked. */
@Testcontainers
@SpringJUnitWebConfig({PayOsWebhookControllerTest.TestConfig.class, PayOsWebhookController.class})
class PayOsWebhookControllerTest {

    private static final String PATH = "/api/v1/payments/payos/webhook";
    private static final Instant FIRST = Instant.parse("2026-10-19T03:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-19T03:07:00Z");
    // Vietnamese text: the raw body must reach the gateway byte-for-byte (the signature covers its content).
    private static final String BODY = "{\"code\":\"00\",\"desc\":\"Thanh toán đơn hàng\",\"signature\":\"sig\"}";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(TestPostgres.IMAGE);
    private static final FakeGateway GATEWAY = new FakeGateway();
    private static final Clock CLOCK = Mockito.mock(Clock.class);

    @Autowired JdbcTemplate jdbc;
    @Autowired ShopRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        GATEWAY.failure = null;
        GATEWAY.lastBody = null;
        Mockito.when(CLOCK.instant()).thenReturn(FIRST);
    }

    @Test
    void invalidSignatureIs400AndChangesNothing() throws Exception {
        Order order = newPayosOrder();
        GATEWAY.failure = new InvalidWebhookException("PayOS webhook rejected: forged");

        webhook(BODY).andExpect(status().isBadRequest()).andExpect(content().string(""));

        assertThat(reload(order).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reload(order).paidAt()).isNull();
    }

    @Test
    void disabledGatewayIs400NotAServerError() throws Exception {
        GATEWAY.failure = new IllegalStateException("PayOS chưa được cấu hình");

        webhook(BODY).andExpect(status().isBadRequest()).andExpect(content().string(""));
    }

    @Test
    void paidWebhookMarksOrderPaid() throws Exception {
        Order order = newPayosOrder();
        GATEWAY.payment = new VerifiedPayment(order.payosOrderCode(), order.totalVnd(), "REF-1", true);

        webhook(BODY).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        Order paid = reload(order);
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.paidAt()).isEqualTo(FIRST);
        assertThat(paid.paymentReference()).isEqualTo("REF-1");
        assertThat(GATEWAY.lastBody).isEqualTo(BODY);
    }

    @Test
    void duplicateWebhookIsHarmless() throws Exception {
        Order order = newPayosOrder();
        GATEWAY.payment = new VerifiedPayment(order.payosOrderCode(), order.totalVnd(), "REF-1", true);

        webhook(BODY).andExpect(status().isOk());
        Mockito.when(CLOCK.instant()).thenReturn(LATER);
        webhook(BODY).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));

        Order paid = reload(order);
        assertThat(paid.status()).isEqualTo("PAID");
        assertThat(paid.paidAt()).isEqualTo(FIRST);
        assertThat(paid.paymentReference()).isEqualTo("REF-1");
    }

    @Test
    void unknownOrderCodeIs200() throws Exception {
        // PayOS sends a sample payment when the webhook URL is confirmed.
        GATEWAY.payment = new VerifiedPayment(123L, 3000, "TEST", true);

        webhook(BODY).andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void amountMismatchIs200ButNotPaid() throws Exception {
        Order order = newPayosOrder();
        GATEWAY.payment = new VerifiedPayment(order.payosOrderCode(), order.totalVnd() - 1, "REF-1", true);

        webhook(BODY).andExpect(status().isOk());

        assertThat(reload(order).status()).isEqualTo("PENDING_PAYMENT");
        assertThat(reload(order).paidAt()).isNull();
    }

    @Test
    void unsuccessfulPaymentIs200AndChangesNothing() throws Exception {
        Order order = newPayosOrder();
        GATEWAY.payment = new VerifiedPayment(order.payosOrderCode(), order.totalVnd(), "REF-1", false);

        webhook(BODY).andExpect(status().isOk());

        assertThat(reload(order).status()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void webhookNeedsNoSessionOrCsrf() throws Exception {
        // control: the same chain does enforce CSRF on an ordinary POST...
        mvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        // ...but the webhook (no cookie, no CSRF header) gets past it and is judged by its signature only.
        GATEWAY.failure = new InvalidWebhookException("PayOS webhook rejected: forged");
        webhook(BODY).andExpect(status().isBadRequest());
    }

    private ResultActions webhook(String body) throws Exception {
        return mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Order reload(Order order) {
        return repository.adminOrder(order.id()).orElseThrow();
    }

    /** Price 100000 + 30000 shipping = 130000. */
    private Order newPayosOrder() {
        UUID user = jdbc.queryForObject("INSERT INTO app_user (email, full_name) VALUES (?, 'T') RETURNING id",
                UUID.class, "wh-" + UUID.randomUUID() + "@example.invalid");
        UUID product = jdbc.queryForObject("""
                INSERT INTO shop_product (sku, name, description, price_vnd, stock_quantity, status)
                VALUES (?, 'Webhook test product', 'test', 100000, 5, 'PUBLISHED') RETURNING id
                """, UUID.class, "WH-" + UUID.randomUUID());
        jdbc.update("INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, 1)", user, product);
        return repository.createOrder(user, "PAYOS", "A", "0900000000", "Addr", FIRST.plusSeconds(300), 3);
    }

    /** Verdict and payload are set per test; the real adapter's SDK verification is covered by PayOsPaymentGatewayTest. */
    private static final class FakeGateway implements PaymentGateway {
        VerifiedPayment payment;
        RuntimeException failure;
        String lastBody;

        @Override public VerifiedPayment verifyWebhook(String rawBody) {
            lastBody = rawBody;
            if (failure != null) throw failure;
            return payment;
        }

        @Override public boolean enabled() { return true; }
        @Override public PaymentLink createLink(long code, long amountVnd, String description, Instant expiresAt,
                                                URI returnUrl, URI cancelUrl) { throw new UnsupportedOperationException(); }
        @Override public void cancelLink(long code, String reason) { throw new UnsupportedOperationException(); }
        @Override public boolean isPaid(long code) { throw new UnsupportedOperationException(); }
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableTransactionManagement
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean DataSource dataSource() {
            Flyway.configure()
                    .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                    .locations("classpath:db/migration", "classpath:db/seed")
                    .load().migrate();
            DriverManagerDataSource ds = new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            ds.setDriverClassName("org.postgresql.Driver");
            return ds;
        }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplateConfig().jdbcTemplate(ds); }
        @Bean ShopRepository shopRepository(JdbcTemplate jdbc) { return new JdbcShopRepository(jdbc); }
        @Bean PaymentWebhookService paymentWebhookService(ShopRepository repository) {
            return new PaymentWebhookService(GATEWAY, repository, CLOCK);
        }
        @Bean Clock clock() { return CLOCK; }

        // Collaborators SecurityConfig needs; none of them matters to the webhook.
        @Bean CookieCsrfTokenRepository csrfTokenRepository() { return CookieCsrfTokenRepository.withHttpOnlyFalse(); }
        @Bean CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() { return new CsrfTokenRequestAttributeHandler(); }
        @Bean AdminAccessGate adminAccessGate() { return (request, response, chain) -> chain.doFilter(request, response); }
        @Bean MessageResolver messageResolver() { return new MessageResolver(new StaticMessageSource()); }
        @Bean LocaleResolver localeResolver() { return new AcceptHeaderLocaleResolver(); }
        @Bean JsonMapper jsonMapper() {
            return JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        }
    }
}
