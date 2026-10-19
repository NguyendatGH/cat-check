package com.catcheck.shop.infrastructure.payment;

import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import vn.payos.model.v2.paymentRequests.PaymentLinkStatus;
import vn.payos.model.webhooks.WebhookData;

import java.net.URI;
import java.time.Instant;

class PayOsPaymentGateway implements PaymentGateway, AutoCloseable {

    private final PayOS client;

    PayOsPaymentGateway(PayOS client) {
        this.client = client;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public PaymentLink createLink(long payosOrderCode, long amountVnd, String description, Instant expiresAt,
                                  URI returnUrl, URI cancelUrl) {
        // payos.vn docs: description max 9 chars for non-payOS-linked bank accounts (none stated for linked); expiredAt is an Int32 unix timestamp (seconds), no minimum gap stated.
        CreatePaymentLinkResponse response = client.paymentRequests().create(CreatePaymentLinkRequest.builder()
                .orderCode(payosOrderCode)
                .amount(amountVnd)
                .description(description)
                .returnUrl(returnUrl.toString())
                .cancelUrl(cancelUrl.toString())
                .expiredAt(expiresAt.getEpochSecond())
                .build());
        return new PaymentLink(response.getPaymentLinkId(), response.getCheckoutUrl());
    }

    @Override
    public void cancelLink(long payosOrderCode, String reason) {
        client.paymentRequests().cancel(payosOrderCode, reason);
    }

    @Override
    public boolean isPaid(long payosOrderCode) {
        return client.paymentRequests().get(payosOrderCode).getStatus() == PaymentLinkStatus.PAID;
    }

    @Override
    public VerifiedPayment verifyWebhook(String rawBody) {
        try {
            WebhookData data = client.webhooks().verify(rawBody);
            return new VerifiedPayment(data.getOrderCode(), data.getAmount(), data.getReference(), "00".equals(data.getCode()));
        } catch (RuntimeException ex) {
            // class name only: SDK/Jackson messages can echo body fragments (payer name, account number).
            throw new InvalidWebhookException("PayOS webhook rejected: " + ex.getClass().getSimpleName());
        }
    }

    @Override
    public void close() {
        client.close();
    }
}
