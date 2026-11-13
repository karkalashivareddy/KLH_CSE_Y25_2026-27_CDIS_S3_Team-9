package com.cloudresilience.modules.cart.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CartService {
    public record CartItemView(UUID itemId, UUID productId, String sku, String name, String status,
            BigDecimal unitPrice, String currency, int quantity, BigDecimal lineTotal,
            long availableQuantity, boolean productAvailable) {}
    public record CartView(UUID cartId, List<CartItemView> items, int itemCount, long totalQuantity,
            BigDecimal subtotal, String currency) {}
    public record CartSummary(int itemCount, long totalQuantity, BigDecimal subtotal, String currency) {}

    private final JdbcTemplate jdbc;
    public CartService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public CartView get(UUID userId) {
        UUID cartId = findOrCreateAndLock(userId);
        return view(cartId);
    }

    @Transactional(readOnly = true)
    public CartSummary summary(UUID userId) {
        return calculateSummary(findActiveCart(userId));
    }

    @Transactional
    public CartView add(UUID userId, UUID productId, int quantity) {
        UUID cartId = findOrCreateAndLock(userId);
        var products = jdbc.query("""
                SELECT p.id FROM products p JOIN categories c ON c.id=p.category_id
                WHERE p.id=? AND p.status='ACTIVE' AND c.active FOR SHARE OF p,c
                """, (rs, row) -> rs.getObject(1, UUID.class), productId);
        if (products.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Active product not found");

        var existing = jdbc.query("SELECT id,quantity FROM cart_items WHERE cart_id=? AND product_id=? FOR UPDATE",
                (rs, row) -> new ExistingItem(rs.getObject("id", UUID.class), rs.getInt("quantity")), cartId, productId);
        if (existing.isEmpty()) {
            jdbc.update("INSERT INTO cart_items(id,cart_id,product_id,quantity) VALUES(?,?,?,?)",
                    UUID.randomUUID(), cartId, productId, quantity);
        } else {
            int next = Math.addExact(existing.getFirst().quantity(), quantity);
            if (next > 9999) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart quantity exceeds the limit");
            jdbc.update("UPDATE cart_items SET quantity=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", next, existing.getFirst().id());
        }
        touch(cartId);
        return view(cartId);
    }

    @Transactional
    public CartView update(UUID userId, UUID itemId, int quantity) {
        UUID cartId = findOrCreateAndLock(userId);
        int changed = jdbc.update("UPDATE cart_items SET quantity=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND cart_id=?",
                quantity, itemId, cartId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        touch(cartId);
        return view(cartId);
    }

    @Transactional
    public CartView remove(UUID userId, UUID itemId) {
        UUID cartId = findOrCreateAndLock(userId);
        if (jdbc.update("DELETE FROM cart_items WHERE id=? AND cart_id=?", itemId, cartId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cart item not found");
        touch(cartId);
        return view(cartId);
    }

    @Transactional
    public CartView clear(UUID userId) {
        UUID cartId = findOrCreateAndLock(userId);
        jdbc.update("DELETE FROM cart_items WHERE cart_id=?", cartId);
        touch(cartId);
        return view(cartId);
    }

    private UUID findOrCreateAndLock(UUID userId) {
        UUID candidate = UUID.randomUUID();
        jdbc.update("INSERT INTO carts(id,user_id,status) VALUES(?,?,'ACTIVE') ON CONFLICT (user_id) WHERE status='ACTIVE' DO NOTHING",
                candidate, userId);
        return jdbc.query("SELECT id FROM carts WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, userId);
    }

    private UUID findActiveCart(UUID userId) {
        return jdbc.query("SELECT id FROM carts WHERE user_id=? AND status='ACTIVE'",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, userId);
    }

    private CartView view(UUID cartId) {
        if (cartId == null) return new CartView(null, List.of(), 0, 0, BigDecimal.ZERO, "USD");
        List<CartItemView> items = jdbc.query("""
                SELECT ci.id item_id,p.id product_id,p.sku,p.name,p.status,p.price,p.currency,ci.quantity,
                       (p.price * ci.quantity) line_total,COALESCE(i.quantity_on_hand-i.quantity_reserved,0) available_quantity,
                       (p.status='ACTIVE' AND c.active) product_available
                FROM cart_items ci JOIN products p ON p.id=ci.product_id JOIN categories c ON c.id=p.category_id
                LEFT JOIN inventory i ON i.product_id=p.id WHERE ci.cart_id=? ORDER BY ci.created_at,ci.id
                """, (rs, row) -> new CartItemView(rs.getObject("item_id", UUID.class),
                rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("name"),
                rs.getString("status"), rs.getBigDecimal("price"), rs.getString("currency").trim(),
                rs.getInt("quantity"), rs.getBigDecimal("line_total"), rs.getLong("available_quantity"),
                rs.getBoolean("product_available")), cartId);
        BigDecimal subtotal = items.stream().map(CartItemView::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        long quantity = items.stream().mapToLong(CartItemView::quantity).sum();
        return new CartView(cartId, List.copyOf(items), items.size(), quantity, subtotal, "USD");
    }

    private CartSummary calculateSummary(UUID cartId) {
        if (cartId == null) return new CartSummary(0, 0, BigDecimal.ZERO, "USD");
        return jdbc.query("""
                SELECT count(*) item_count,COALESCE(sum(quantity),0) total_quantity,
                COALESCE(sum(p.price*ci.quantity),0) subtotal FROM cart_items ci JOIN products p ON p.id=ci.product_id WHERE ci.cart_id=?
                """,
                rs -> { rs.next(); return new CartSummary(rs.getInt("item_count"), rs.getLong("total_quantity"), rs.getBigDecimal("subtotal"), "USD"); }, cartId);
    }

    private void touch(UUID cartId) { jdbc.update("UPDATE carts SET updated_at=CURRENT_TIMESTAMP WHERE id=?", cartId); }
    private record ExistingItem(UUID id, int quantity) {}
}
