package com.catcheck.shop.application;

import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;
import com.catcheck.shop.domain.port.ShopRepository;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ShopService {
    private final ShopRepository repository;
    public ShopService(ShopRepository repository) { this.repository = repository; }
    public List<Product> products(String query, int limit) { return repository.products(query, limit); }
    public Product product(UUID id) { return repository.product(id).orElseThrow(() -> new NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND)); }
    public List<CartLine> cart(UUID userId) { return repository.cart(userId); }
    public void setLine(UUID userId, UUID productId, int quantity) { if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("quantity"); repository.setCartLine(userId, productId, quantity); }
    public void removeLine(UUID userId, UUID productId) { repository.removeCartLine(userId, productId); }
    public void clearCart(UUID userId) { repository.clearCart(userId); }
    public Order checkout(UUID userId, String paymentMethod, String name, String phone, String address) { return repository.createOrder(userId, paymentMethod, name.strip(), phone.strip(), address.strip()); }
    public Order order(UUID userId, UUID orderId) { return repository.order(userId, orderId).orElseThrow(() -> new NotFoundException(ShopErrorCode.ORDER_NOT_FOUND)); }
}
