package com.catcheck.shop.infrastructure.payment;

import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.payos.model.webhooks.Webhook;
import vn.payos.model.webhooks.WebhookData;

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
            assertThatThrownBy(() -> gateway.isPaid(1L)).isInstanceOf(IllegalStateException.class);
        });
    }

    private ApplicationContextRunner runner(String checksumKeyProperty) {
        return new ApplicationContextRunner()
                .withUserConfiguration(PaymentGatewayConfiguration.class)
                .withPropertyValues("catcheck.shop.payos.client-id=c", "catcheck.shop.payos.api-key=k", checksumKeyProperty);
    }
}
