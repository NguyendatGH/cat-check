package com.catcheck.shop.infrastructure.payment;

import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.VerifiedPayment;
import com.catcheck.shop.domain.port.PaymentGateway;

import java.net.URI;
import java.time.Instant;

class DisabledPaymentGateway implements PaymentGateway {

    private static final String MESSAGE = "PayOS chưa được cấu hình: thiếu catcheck.shop.payos.client-id/api-key/checksum-key "
            + "(PAYOS_CLIENT_ID, PAYOS_API_KEY, PAYOS_CHECKSUM_KEY).";

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public PaymentLink createLink(long payosOrderCode, long amountVnd, String description, Instant expiresAt,
                                  URI returnUrl, URI cancelUrl) {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public void cancelLink(long payosOrderCode, String reason) {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public boolean isPaid(long payosOrderCode) {
        throw new IllegalStateException(MESSAGE);
    }

    @Override
    public VerifiedPayment verifyWebhook(String rawBody) {
        throw new IllegalStateException(MESSAGE);
    }
}
