package com.catcheck.shop.domain.port;

import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.ExpiredPayment;
import com.catcheck.shop.domain.MarkPaidResult;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;
import com.catcheck.shop.domain.PromoCode;
import com.catcheck.shop.domain.PromoCodeDraft;

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
    default Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address,
                              Instant paymentExpiresAt, int maxOpenOrders) {
        return createOrder(userId, paymentMethod, receiverName, receiverPhone, address, null, paymentExpiresAt, maxOpenOrders);
    }
    Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address,
                      String promoCode, Instant paymentExpiresAt, int maxOpenOrders);
    Optional<Order> order(UUID userId, UUID orderId);
    List<Order> orders(UUID userId, int limit);
    void attachPaymentLink(UUID orderId, String paymentLinkId, String checkoutUrl);
    /** Compare-and-set on status; false when the order is not in {@code fromStatus}. */
    boolean transition(UUID orderId, String fromStatus, String toStatus);
    /**
     * Cancels and returns stock in one transaction; false when the order is not in {@code expectedStatus}.
     * Flags refund_needed when the order was paid or {@code moneyReceived} (a partial PayOS transfer never sets paid_at).
     */
    boolean cancelAndRestock(UUID orderId, String expectedStatus, String cancelReason, Instant at, boolean moneyReceived);
    MarkPaidResult markPaid(long payosOrderCode, long amountVnd, String reference, Instant paidAt);
    /** Stored PayOS transaction reference; empty when unpaid or the code is unknown. */
    Optional<String> paymentReference(long payosOrderCode);
    List<ExpiredPayment> expiredPayosOrders(Instant now, int limit);

    List<Order> adminOrders(String statusOrNull, int limit, int offset);
    Optional<Order> adminOrder(UUID orderId);

    List<AdminProduct> adminProducts();
    AdminProduct adminCreate(AdminProductDraft draft);
    Optional<AdminProduct> adminLockProduct(UUID id);
    Optional<AdminProduct> adminUpdate(UUID id, AdminProductDraft draft);
    Optional<AdminProduct> adminSetStatus(UUID id, String status);

    default List<PromoCode> publicPromoCodes(Instant now) { return List.of(); }
    default List<PromoCode> adminPromoCodes() { return List.of(); }
    default Optional<PromoCode> adminPromoCode(UUID id) { return Optional.empty(); }
    default PromoCode adminCreatePromoCode(PromoCodeDraft draft) { throw new UnsupportedOperationException(); }
    default Optional<PromoCode> adminUpdatePromoCode(UUID id, PromoCodeDraft draft) { return Optional.empty(); }
    default Optional<PromoCode> adminSetPromoCodeStatus(UUID id, boolean isActive) { return Optional.empty(); }
    default boolean adminDeletePromoCode(UUID id) { return false; }
}
