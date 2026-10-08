package com.catcheck.shop.domain.port;

import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;

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
    Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address);
    Optional<Order> order(UUID userId, UUID orderId);

    List<AdminProduct> adminProducts();
    AdminProduct adminCreate(AdminProductDraft draft);
    Optional<AdminProduct> adminUpdate(UUID id, AdminProductDraft draft);
    Optional<AdminProduct> adminSetStatus(UUID id, String status);
}
