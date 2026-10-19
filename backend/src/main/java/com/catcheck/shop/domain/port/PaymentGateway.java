package com.catcheck.shop.domain.port;

import com.catcheck.shop.domain.InvalidWebhookException;
import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.VerifiedPayment;

import java.net.URI;
import java.time.Instant;

/** Payment provider port (PayOS). When {@link #enabled()} is false every other method throws IllegalStateException. */
public interface PaymentGateway {

    boolean enabled();

    PaymentLink createLink(long payosOrderCode, long amountVnd, String description, Instant expiresAt,
                           URI returnUrl, URI cancelUrl);

    void cancelLink(long payosOrderCode, String reason);

    boolean isPaid(long payosOrderCode);

    /** Checks the signature and parses the body; throws {@link InvalidWebhookException} when either fails. */
    VerifiedPayment verifyWebhook(String rawBody);
}
