package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.application.OrderViews.AdminSummary;
import com.cloudresilience.modules.orders.application.OrderViews.History;
import com.cloudresilience.modules.orders.application.OrderViews.Item;
import com.cloudresilience.modules.orders.application.OrderViews.Order;
import com.cloudresilience.modules.orders.application.OrderViews.Page;
import com.cloudresilience.modules.orders.application.OrderViews.Payment;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderQueryService {
    private final JdbcTemplate jdbc;
    public OrderQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public Order customerOrder(UUID customerId, UUID orderId) {
        return load("SELECT * FROM customer_orders WHERE id=? AND customer_id=?", orderId, customerId);
    }

    @Transactional(readOnly = true)
    public Page<Order> customerOrders(UUID customerId, int page, int size) {
        validatePage(page, size);
        long total = jdbc.queryForObject("SELECT count(*) FROM customer_orders WHERE customer_id=?", Long.class, customerId);
        List<UUID> ids = jdbc.query("""
                SELECT id FROM customer_orders WHERE customer_id=?
                ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> rs.getObject(1, UUID.class), customerId, size, Math.multiplyExact(page, size));
        return page(ids.stream().map(id -> load("SELECT * FROM customer_orders WHERE id=? AND customer_id=?", id, customerId)).toList(), page, size, total);
    }

    @Transactional(readOnly = true)
    public Order adminOrder(UUID orderId) { return load("SELECT * FROM customer_orders WHERE id=?", orderId); }

    @Transactional(readOnly = true)
    public Page<Order> adminOrders(int page, int size, OrderStatus status, String query) {
        validatePage(page, size);
        String term = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(java.util.Locale.ROOT) + "%";
        String state = status == null ? null : status.name();
        long total = jdbc.queryForObject("""
                SELECT count(*) FROM customer_orders o JOIN users u ON u.id=o.customer_id
                WHERE (? IS NULL OR o.status=?) AND (? IS NULL OR lower(o.order_number) LIKE ? OR lower(u.email) LIKE ?)
                """, Long.class, state, state, term, term, term);
        List<UUID> ids = jdbc.query("""
                SELECT o.id FROM customer_orders o JOIN users u ON u.id=o.customer_id
                WHERE (? IS NULL OR o.status=?) AND (? IS NULL OR lower(o.order_number) LIKE ? OR lower(u.email) LIKE ?)
                ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> rs.getObject(1, UUID.class), state, state, term, term, term, size,
                Math.multiplyExact(page, size));
        return page(ids.stream().map(this::adminOrder).toList(), page, size, total);
    }

    @Transactional(readOnly = true)
    public AdminSummary summary() {
        Map<String, Long> counts = jdbc.query("SELECT status,count(*) total FROM customer_orders GROUP BY status ORDER BY status",
                (rs, row) -> Map.entry(rs.getString("status"), rs.getLong("total"))).stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new AdminSummary(counts.values().stream().mapToLong(Long::longValue).sum(), counts);
    }

    @Transactional(readOnly = true)
    public Payment customerPayment(UUID customerId, UUID orderId) {
        var rows = jdbc.query("""
                SELECT p.id,p.status,p.amount,p.currency,p.failure_category,p.created_at,p.updated_at
                FROM payments p JOIN customer_orders o ON o.id=p.order_id
                WHERE o.id=? AND o.customer_id=?
                """, (rs, row) -> new Payment(rs.getObject("id", UUID.class), PaymentStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("amount"), rs.getString("currency").trim(), rs.getString("failure_category"), true,
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), orderId, customerId);
        if (rows.isEmpty()) throw notFound();
        return rows.getFirst();
    }

    private Order load(String sql, Object... args) {
        var rows = jdbc.query(sql, (rs, row) -> mapOrder(rs), args);
        if (rows.isEmpty()) throw notFound();
        Order order = rows.getFirst();
        List<Item> items = jdbc.query("""
                SELECT id,product_id,sku_snapshot,product_name_snapshot,unit_price,quantity,line_total,created_at
                FROM order_items WHERE order_id=? ORDER BY created_at,id
                """, (rs, row) -> new Item(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("sku_snapshot"), rs.getString("product_name_snapshot"), rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"), rs.getBigDecimal("line_total"), rs.getTimestamp("created_at").toInstant()), order.id());
        List<History> history = jdbc.query("""
                SELECT id,from_status,to_status,reason,created_at FROM order_status_history
                WHERE order_id=? ORDER BY created_at,id
                """, (rs, row) -> new History(rs.getObject("id", UUID.class),
                rs.getString("from_status") == null ? null : OrderStatus.valueOf(rs.getString("from_status")),
                OrderStatus.valueOf(rs.getString("to_status")), rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant()), order.id());
        return new Order(order.id(), order.orderNumber(), order.status(), order.paymentStatus(), order.subtotal(),
                order.discountAmount(), order.taxAmount(), order.shippingAmount(), order.totalAmount(), order.currency(),
                order.cancellationReason(), order.createdAt(), order.updatedAt(), List.copyOf(items), List.copyOf(history));
    }

    private Order mapOrder(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Order(rs.getObject("id", UUID.class), rs.getString("order_number"),
                OrderStatus.valueOf(rs.getString("status")), PaymentStatus.valueOf(rs.getString("payment_status")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("discount_amount"), rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("shipping_amount"), rs.getBigDecimal("total_amount"), rs.getString("currency").trim(),
                rs.getString("cancellation_reason"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(), List.of(), List.of());
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or size");
    }

    private <T> Page<T> page(List<T> content, int page, int size, long total) {
        return new Page<>(content, page, size, total, (int) Math.ceil((double) total / size));
    }

    private ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"); }
}
