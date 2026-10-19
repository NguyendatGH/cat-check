package com.catcheck.shop.domain.port;

import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.ExpiredPayment;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository {
    List<Product> products(String query, int limit);
    Optional<Product> product(UUID productId);
    List<CartLine> cart(UUID userId);
    void setCartLine(UUID userId, UUID productId, int quantity);
    void removeCartLine(UUID userId, UUID productId);
    void clearCart(UUID userId);
    /** Throws ORDER_LIMIT_REACHED when the user already has {@code maxOpenOrders} PENDING_PAYMENT orders. */
    Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address,
                      Instant paymentExpiresAt, int maxOpenOrders);
    Optional<Order> order(UUID userId, UUID orderId);
    List<Order> orders(UUID userId, int limit);
    void attachPaymentLink(UUID orderId, String paymentLinkId, String checkoutUrl);
    /** Compare-and-set on status; false when the order is not in {@code fromStatus}. */
    boolean transition(UUID orderId, String fromStatus, String toStatus);
    /** Cancels and returns stock in one transaction; false when the order is not in {@code expectedStatus}. */
    boolean cancelAndRestock(UUID orderId, String expectedStatus, String cancelReason, Instant at);
    MarkPaidResult markPaid(long payosOrderCode, long amountVnd, String reference, Instant paidAt);
    List<ExpiredPayment> expiredPayosOrders(Instant now, int limit);

    List<Order> adminOrders(String statusOrNull, int limit, int offset);
    Optional<Order> adminOrder(UUID orderId);

    List<AdminProduct> adminProducts();
    AdminProduct adminCreate(AdminProductDraft draft);
    Optional<AdminProduct> adminLockProduct(UUID id);
    Optional<AdminProduct> adminUpdate(UUID id, AdminProductDraft draft);
    Optional<AdminProduct> adminSetStatus(UUID id, String status);
}
