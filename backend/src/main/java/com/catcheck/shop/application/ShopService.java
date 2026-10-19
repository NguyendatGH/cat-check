package com.catcheck.shop.application;

import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.OrderStatusPolicy;
import com.catcheck.shop.domain.PaymentLink;
import com.catcheck.shop.domain.Product;
import com.catcheck.shop.domain.port.PaymentGateway;
import com.catcheck.shop.domain.port.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ShopService {
    private static final Logger log = LoggerFactory.getLogger(ShopService.class);
    private static final int MY_ORDERS_LIMIT = 50;

    private final ShopRepository repository;
    private final PaymentGateway gateway;
    private final PayOsProperties payos;
    private final Clock clock;
    private final int maxOpenOrders;
    private final String webBaseUrl;

    public ShopService(ShopRepository repository, PaymentGateway gateway, PayOsProperties payos, Clock clock,
                       @Value("${catcheck.shop.max-open-orders:3}") int maxOpenOrders,
                       @Value("${catcheck.shop.web-base-url:http://localhost:5173}") String webBaseUrl) {
        this.repository = repository;
        this.gateway = gateway;
        this.payos = payos;
        this.clock = clock;
        this.maxOpenOrders = maxOpenOrders;
        this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
    }

    public List<Product> products(String query, int limit) { return repository.products(query, limit); }
    public Product product(UUID id) { return repository.product(id).orElseThrow(() -> new NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND)); }
    public List<CartLine> cart(UUID userId) { return repository.cart(userId); }
    public void setLine(UUID userId, UUID productId, int quantity) { if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("quantity"); repository.setCartLine(userId, productId, quantity); }
    public void removeLine(UUID userId, UUID productId) { repository.removeCartLine(userId, productId); }
    public void clearCart(UUID userId) { repository.clearCart(userId); }

    /** One transaction: a PayOS failure rolls back the order, the stock deduction and the cart clearing. */
    @Transactional
    public Order checkout(UUID userId, String paymentMethod, String name, String phone, String address) {
        boolean payosOrder = "PAYOS".equals(paymentMethod);
        if (payosOrder && !gateway.enabled()) throw new BusinessRuleException(ShopErrorCode.PAYMENT_UNAVAILABLE);
        Instant expiresAt = payosOrder ? clock.instant().plus(payos.paymentTtl()) : null;
        Order order = repository.createOrder(userId, paymentMethod, name.strip(), phone.strip(), address.strip(), expiresAt, maxOpenOrders);
        if (!payosOrder) return order;
        PaymentLink link;
        try {
            long code = order.payosOrderCode();
            String orderUrl = webBaseUrl + "/orders/" + order.id();
            link = gateway.createLink(code, order.totalVnd(), "CC" + code, expiresAt,
                    URI.create(orderUrl + "?payment=return"), URI.create(orderUrl + "?payment=cancel"));
        } catch (RuntimeException e) {
            log.warn("PayOS createLink failed for order {}", order.id(), e);
            throw new BusinessRuleException(ShopErrorCode.PAYMENT_UNAVAILABLE, e);
        }
        repository.attachPaymentLink(order.id(), link.paymentLinkId(), link.checkoutUrl());
        return repository.order(userId, order.id()).orElseThrow();
    }

    public Order order(UUID userId, UUID orderId) { return repository.order(userId, orderId).orElseThrow(() -> new NotFoundException(ShopErrorCode.ORDER_NOT_FOUND)); }

    public List<Order> myOrders(UUID userId) { return repository.orders(userId, MY_ORDERS_LIMIT); }

    /** Not transactional on purpose: the PayOS call must not hold a database transaction open. */
    public Order cancelByBuyer(UUID userId, UUID orderId) {
        Order order = order(userId, orderId);
        if (!OrderStatusPolicy.buyerCanCancel(order.status())) throw new BusinessRuleException(ShopErrorCode.ORDER_NOT_CANCELLABLE);
        if (order.payosOrderCode() != null) {
            try {
                gateway.cancelLink(order.payosOrderCode(), "BUYER_CANCELLED");
            } catch (RuntimeException e) {
                // The link expires on its own; a paid-meanwhile order is caught by the status check below.
                log.warn("PayOS cancelLink failed for order {} (code {})", orderId, order.payosOrderCode(), e);
            }
        }
        if (!repository.cancelAndRestock(orderId, "PENDING_PAYMENT", "BUYER", clock.instant(), false)) throw new BusinessRuleException(ShopErrorCode.ORDER_NOT_CANCELLABLE);
        return order(userId, orderId);
    }
}
