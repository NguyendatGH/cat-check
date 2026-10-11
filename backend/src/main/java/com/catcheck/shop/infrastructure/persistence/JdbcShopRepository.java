package com.catcheck.shop.infrastructure.persistence;

import com.catcheck.shop.api.ShopErrorCode;
import com.catcheck.shop.domain.AdminProduct;
import com.catcheck.shop.domain.AdminProductDraft;
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
import java.util.ArrayList;
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
        Product product = product(productId)
                .orElseThrow(() -> new com.catcheck.shared.error.NotFoundException(ShopErrorCode.PRODUCT_NOT_FOUND));
        if (quantity > product.stockQuantity()) throw new ConflictException(ShopErrorCode.STOCK_UNAVAILABLE);
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
        // FOR UPDATE tren CA HAI bang (khong chi `p`): neu chi khoa shop_product, mot double-submit
        // (double-click "Dat hang") tao hai transaction doc cung shop_cart_line se khong duoc Postgres
        // phat hien dung — giao dich thu hai, sau khi bi chan boi khoa tren `p` roi duoc mo khoa, van
        // dung lai dong shop_cart_line CU (da bi giao dich dau xoa) thay vi nhan ra gio hang da rong,
        // va tao ra DON HANG THU HAI. Xac nhan bang thuc nghiem truc tiep tren Postgres that (hai
        // session dong thoi): FOR UPDATE OF p -> session thu hai van thay dong gio hang da xoa; FOR
        // UPDATE (ca hai bang) -> session thu hai dung thay 0 dong, nem CART_EMPTY nhu mong doi.
        // Dong gio hang van con sau khi admin chuyen san pham sang DRAFT/ARCHIVED (setCartLine chi
        // chan luc them) — doc status trong cung cau khoa de khong dat duoc hang da ngung ban.
        List<UUID> notPublished = new ArrayList<>();
        List<CartLine> lines = jdbc.query("""
                SELECT p.id, p.sku, p.name, p.description, p.image_url, p.price_vnd, p.compare_at_price_vnd,
                       p.stock_quantity, p.status, c.quantity
                  FROM shop_cart_line c JOIN shop_product p ON p.id = c.product_id
                 WHERE c.user_id = ? FOR UPDATE
                """, (rs, row) -> {
                    CartLine line = new CartLine(product(rs), rs.getInt("quantity"));
                    if (!"PUBLISHED".equals(rs.getString("status"))) notPublished.add(line.product().id());
                    return line;
                }, userId);
        if (lines.isEmpty()) throw new ConflictException(ShopErrorCode.CART_EMPTY);
        if (!notPublished.isEmpty()) throw new ConflictException(ShopErrorCode.STOCK_UNAVAILABLE);
        for (CartLine line : lines) {
            if (line.quantity() > line.product().stockQuantity()) throw new ConflictException(ShopErrorCode.STOCK_UNAVAILABLE);
        }
        long subtotal = lines.stream().mapToLong(CartLine::totalVnd).sum();
        long shipping = subtotal >= 500000 ? 0 : 30000;
        // order_code lay 10 hex NGAU NHIEN cua gen_random_uuid(). Truoc day lay 10 hex DAU cua uuidv7()
        // = phan timestamp (do phan giai ~256 ms): hai don bat ky trong cung ~256 ms trung ma -> 500.
        UUID id = jdbc.queryForObject("""
                INSERT INTO shop_order (order_code, user_id, payment_method, receiver_name, receiver_phone, shipping_address, subtotal_vnd, shipping_fee_vnd, total_vnd)
                VALUES ('CC-' || upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 10)), ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
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
        List<CartLine> lines = jdbc.query("SELECT p.id, p.sku, l.product_name AS name, p.description, p.image_url, l.unit_price_vnd AS price_vnd, p.compare_at_price_vnd, p.stock_quantity, l.quantity FROM shop_order_line l JOIN shop_product p ON p.id = l.product_id WHERE l.order_id = ?", (child, row) -> new CartLine(product(child), child.getInt("quantity")), id);
        return new Order(id, rs.getString("order_code"), rs.getString("status"), rs.getString("payment_method"), rs.getString("receiver_name"), rs.getString("receiver_phone"), rs.getString("shipping_address"), rs.getLong("subtotal_vnd"), rs.getLong("discount_vnd"), rs.getLong("shipping_fee_vnd"), rs.getLong("total_vnd"), lines, instant(rs, "created_at"));
    }

    private static final String ADMIN_COLS = "id, sku, name, description, image_url, price_vnd, compare_at_price_vnd, stock_quantity, status, updated_at";

    @Override
    public List<AdminProduct> adminProducts() {
        return jdbc.query("SELECT " + ADMIN_COLS + " FROM shop_product ORDER BY updated_at DESC, id DESC", (rs, row) -> adminProduct(rs));
    }

    @Override
    @Transactional
    public AdminProduct adminCreate(AdminProductDraft d) {
        try {
            return jdbc.query("INSERT INTO shop_product (sku, name, description, image_url, price_vnd, compare_at_price_vnd, stock_quantity, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING " + ADMIN_COLS,
                    (rs, row) -> adminProduct(rs), d.sku(), d.name(), d.description(), d.imageUrl(), d.priceVnd(), d.compareAtPriceVnd(), d.stockQuantity(), d.status()).getFirst();
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new ConflictException(ShopErrorCode.PRODUCT_SKU_DUPLICATE);
        }
    }

    @Override
    @Transactional
    public Optional<AdminProduct> adminLockProduct(UUID id) {
        return jdbc.query("SELECT " + ADMIN_COLS + " FROM shop_product WHERE id = ? FOR UPDATE",
                (rs, row) -> adminProduct(rs), id).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<AdminProduct> adminUpdate(UUID id, AdminProductDraft d) {
        return jdbc.query("UPDATE shop_product SET name = ?, description = ?, image_url = ?, price_vnd = ?, compare_at_price_vnd = ?, stock_quantity = ?, status = ? WHERE id = ? RETURNING " + ADMIN_COLS,
                (rs, row) -> adminProduct(rs), d.name(), d.description(), d.imageUrl(), d.priceVnd(), d.compareAtPriceVnd(), d.stockQuantity(), d.status(), id).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<AdminProduct> adminSetStatus(UUID id, String status) {
        return jdbc.query("UPDATE shop_product SET status = ? WHERE id = ? RETURNING " + ADMIN_COLS,
                (rs, row) -> adminProduct(rs), status, id).stream().findFirst();
    }

    private AdminProduct adminProduct(ResultSet rs) throws SQLException {
        long compare = rs.getLong("compare_at_price_vnd");
        boolean compareWasNull = rs.wasNull();
        return new AdminProduct(rs.getObject("id", UUID.class), rs.getString("sku"), rs.getString("name"), rs.getString("description"),
                rs.getString("image_url"), rs.getLong("price_vnd"), compareWasNull ? null : compare, rs.getInt("stock_quantity"),
                rs.getString("status"), instant(rs, "updated_at"));
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
