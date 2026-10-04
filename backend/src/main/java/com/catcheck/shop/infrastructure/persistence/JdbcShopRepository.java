package com.catcheck.shop.infrastructure.persistence;

import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.CartLine;
import com.catcheck.shop.domain.Order;
import com.catcheck.shop.domain.Product;
import com.catcheck.shop.domain.port.ShopRepository;
import com.catcheck.shared.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcShopRepository implements ShopRepository {
    private final JdbcTemplate jdbc;

    public JdbcShopRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Product> products(String query, int limit) {
        if (query == null || query.isBlank()) {
            return jdbc.query("SELECT id, sku, name, description, image_url, price_vnd, compare_at_price_vnd, stock_quantity FROM shop_product WHERE status = 'PUBLISHED' ORDER BY created_at DESC LIMIT ?", (rs, row) -> product(rs), Math.clamp(limit, 1, 100));
        }
        String like = "%" + query.strip() + "%";
        return jdbc.query("SELECT id, sku, name, description, image_url, price_vnd, compare_at_price_vnd, stock_quantity FROM shop_product WHERE status = 'PUBLISHED' AND (name ILIKE ? OR description ILIKE ?) ORDER BY created_at DESC LIMIT ?", (rs, row) -> product(rs), like, like, Math.clamp(limit, 1, 100));
    }

    @Override
    public Optional<Product> product(UUID productId) {
        return jdbc.query("SELECT id, sku, name, description, image_url, price_vnd, compare_at_price_vnd, stock_quantity FROM shop_product WHERE id = ? AND status = 'PUBLISHED'", (rs, row) -> product(rs), productId).stream().findFirst();
    }

    @Override
    public List<CartLine> cart(UUID userId) {
        return jdbc.query("""
                SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price_vnd, p.compare_at_price_vnd,
                       p.stock_quantity, c.quantity
                  FROM shop_cart_line c JOIN shop_product p ON p.id = c.product_id
                 WHERE c.user_id = ? ORDER BY c.updated_at DESC
                """, (rs, row) -> new CartLine(product(rs), rs.getInt("quantity")), userId);
    }

    @Override
    public void setCartLine(UUID userId, UUID productId, int quantity) {
        if (product(productId).isEmpty()) throw new com.catcheck.shared.error.NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND);
        jdbc.update("""
                INSERT INTO shop_cart_line (user_id, product_id, quantity) VALUES (?, ?, ?)
                ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()
                """, userId, productId, quantity);
    }

    @Override
    public void removeCartLine(UUID userId, UUID productId) {
        jdbc.update("DELETE FROM shop_cart_line WHERE user_id = ? AND product_id = ?", userId, productId);
    }

    @Override
    public void clearCart(UUID userId) {
        jdbc.update("DELETE FROM shop_cart_line WHERE user_id = ?", userId);
    }

    @Override
    @Transactional
    public Order createOrder(UUID userId, String paymentMethod, String receiverName, String receiverPhone, String address) {
        List<CartLine> lines = jdbc.query("""
                SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price_vnd, p.compare_at_price_vnd,
                       p.stock_quantity, c.quantity
                  FROM shop_cart_line c JOIN shop_product p ON p.id = c.product_id
                 WHERE c.user_id = ? FOR UPDATE OF p
                """, (rs, row) -> new CartLine(product(rs), rs.getInt("quantity")), userId);
        if (lines.isEmpty()) throw new ConflictException(ShopErrorCode.CART_EMPTY);
        for (CartLine line : lines) {
            if (line.quantity() > line.product().stockQuantity()) throw new ConflictException(ShopErrorCode.STOCK_UNAVAILABLE);
        }
        long subtotal = lines.stream().mapToLong(CartLine::totalVnd).sum();
        long shipping = subtotal >= 500000 ? 0 : 30000;
        UUID id = jdbc.queryForObject("""
                INSERT INTO shop_order (order_code, user_id, payment_method, receiver_name, receiver_phone, shipping_address, subtotal_vnd, shipping_fee_vnd, total_vnd)
                VALUES ('CC-' || upper(substr(replace(uuidv7()::text, '-', ''), 1, 10)), ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, UUID.class, userId, paymentMethod, receiverName, receiverPhone, address, subtotal, shipping, subtotal + shipping);
        String code = jdbc.queryForObject("SELECT order_code FROM shop_order WHERE id = ?", String.class, id);
        for (CartLine line : lines) {
            jdbc.update("INSERT INTO shop_order_line (order_id, product_id, product_name, unit_price_vnd, quantity) VALUES (?, ?, ?, ?, ?)", id, line.product().id(), line.product().name(), line.product().priceVnd(), line.quantity());
            jdbc.update("UPDATE shop_product SET stock_quantity = stock_quantity - ? WHERE id = ?", line.quantity(), line.product().id());
        }
        clearCart(userId);
        return order(userId, id).orElseThrow();
    }

    @Override
    public Optional<Order> order(UUID userId, UUID orderId) {
        List<Order> orders = jdbc.query("SELECT id, order_code, status, payment_method, receiver_name, receiver_phone, shipping_address, subtotal_vnd, discount_vnd, shipping_fee_vnd, total_vnd, created_at FROM shop_order WHERE id = ? AND user_id = ?", (rs, row) -> mapOrder(rs, userId), orderId, userId);
        return orders.stream().findFirst();
    }

    private Order mapOrder(ResultSet rs, UUID userId) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<CartLine> lines = jdbc.query("SELECT p.id, p.sku, l.product_name AS name, '' AS description, p.image_url, l.unit_price_vnd AS price_vnd, NULL AS compare_at_price_vnd, 0 AS stock_quantity, l.quantity FROM shop_order_line l JOIN shop_product p ON p.id = l.product_id WHERE l.order_id = ?", (child, row) -> new CartLine(product(child), child.getInt("quantity")), id);
        return new Order(id, rs.getString("order_code"), rs.getString("status"), rs.getString("payment_method"), rs.getString("receiver_name"), rs.getString("receiver_phone"), rs.getString("shipping_address"), rs.getLong("subtotal_vnd"), rs.getLong("discount_vnd"), rs.getLong("shipping_fee_vnd"), rs.getLong("total_vnd"), lines, instant(rs, "created_at"));
    }

    private Product product(ResultSet rs) throws SQLException {
        long compare = rs.getLong("compare_at_price_vnd");
        boolean compareWasNull = rs.wasNull();
        return new Product(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"), rs.getString("description"), rs.getString("image_url"), rs.getLong("price_vnd"), compareWasNull ? null : compare, rs.getInt("stock_quantity"));
    }

    private static java.time.Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
