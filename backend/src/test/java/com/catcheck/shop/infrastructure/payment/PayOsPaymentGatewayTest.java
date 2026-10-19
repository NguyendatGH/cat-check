package com.catcheck.shop.infrastructure.payment;

import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayOsPaymentGatewayTest {

    private static final String CHECKSUM = "test-checksum-key";

    private final PayOS client = new PayOS(ClientOptions.builder()
            .clientId("test-client-id").apiKey("test-api-key").checksumKey(CHECKSUM).build());
    private final PayOsPaymentGateway gateway = new PayOsPaymentGateway(client);
    private final ObjectMapper json = new ObjectMapper();

    private WebhookData data(String code) {
        return WebhookData.builder().orderCode(100123L).amount(245000L).code(code).desc("success").reference("FT123")
                .description("CC100123").accountNumber("0000").transactionDateTime("2026-10-19 10:00:00")
                .currency("VND").paymentLinkId("link-1").build();
    }

    private String body(WebhookData data, String signature) throws Exception {
        return json.writeValueAsString(Webhook.builder()
                .code(data.getCode()).desc("success").success(true).data(data).signature(signature).build());
    }

    private String signedBody(String code) throws Exception {
        WebhookData data = data(code);
        return body(data, client.getCrypto().createSignatureFromObj(data, CHECKSUM));
    }

    @Test
    void validWebhookIsVerified() throws Exception {
        VerifiedPayment verified = gateway.verifyWebhook(signedBody("00"));

        assertThat(verified).isEqualTo(new VerifiedPayment(100123, 245000, "FT123", true));
    }

    @Test
    void tamperedAmountIsRejected() throws Exception {
        WebhookData signed = data("00");
        String signature = client.getCrypto().createSignatureFromObj(signed, CHECKSUM);
        signed.setAmount(1L);
        String tampered = body(signed, signature);

        assertThatThrownBy(() -> gateway.verifyWebhook(tampered)).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void malformedBodyIsRejected() {
        assertThatThrownBy(() -> gateway.verifyWebhook("not json")).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void nonSuccessCodeIsNotSuccess() throws Exception {
        assertThat(gateway.verifyWebhook(signedBody("01")).success()).isFalse();
    }

    @Test
    void amountPaidReadsThePaymentLink() throws Exception {
        assertThat(amountPaidServedAs(130000L)).isEqualTo(130000L);
        assertThat(amountPaidServedAs(50000L)).as("UNDERPAID link").isEqualTo(50000L);
        assertThat(amountPaidServedAs(0L)).isZero();
        // The SDK rejects a response without amountPaid; the job then retries instead of cancelling a maybe-paid order.
        assertThatThrownBy(() -> amountPaidServedAs(null)).isInstanceOf(RuntimeException.class);
    }

    /** Serves one signed GET /v2/payment-requests/{orderCode} response from a local stub, like PayOS would. */
    private long amountPaidServedAs(Long amountPaid) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "link-1");
        data.put("orderCode", 100123L);
        data.put("amount", 130000L);
        data.put("amountPaid", amountPaid);
        data.put("amountRemaining", amountPaid == null ? 130000L : 130000L - amountPaid);
        data.put("status", "PENDING");
        data.put("createdAt", "2026-10-19T10:00:00+07:00");
        data.put("transactions", List.of());
        data.put("cancellationReason", null);
        data.put("canceledAt", null);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("code", "00");
        envelope.put("desc", "success");
        envelope.put("data", data);
        envelope.put("signature", client.getCrypto().createSignatureFromObj(data, CHECKSUM));
        byte[] response = json.writeValueAsBytes(envelope);

        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v2/payment-requests/100123", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try (PayOsPaymentGateway stubbed = new PayOsPaymentGateway(new PayOS(ClientOptions.builder()
                .clientId("test-client-id").apiKey("test-api-key").checksumKey(CHECKSUM).maxRetries(0)
                .baseURL("http://127.0.0.1:" + server.getAddress().getPort()).build()))) {
            return stubbed.amountPaid(100123L);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void allKeysGiveEnabledPayOsGateway() {
        runner("catcheck.shop.payos.checksum-key=" + CHECKSUM).run(ctx -> {
            assertThat(ctx.getBean(PaymentGateway.class)).isInstanceOf(PayOsPaymentGateway.class);
            assertThat(ctx.getBean(PaymentGateway.class).enabled()).isTrue();
        });
    }

    @Test
    void missingKeysGiveDisabledGateway() {
        runner("catcheck.shop.payos.checksum-key=").run(ctx -> {
            PaymentGateway gateway = ctx.getBean(PaymentGateway.class);
            assertThat(gateway).isInstanceOf(DisabledPaymentGateway.class);
            assertThat(gateway.enabled()).isFalse();
            assertThatThrownBy(() -> gateway.amountPaid(1L)).isInstanceOf(IllegalStateException.class);
        });
    }

    private ApplicationContextRunner runner(String checksumKeyProperty) {
        return new ApplicationContextRunner()
                .withUserConfiguration(PaymentGatewayConfiguration.class)
                .withPropertyValues("catcheck.shop.payos.client-id=c", "catcheck.shop.payos.api-key=k", checksumKeyProperty);
    }
}
