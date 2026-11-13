package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.events.application.OutboxWriter;
import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.domain.OrderEventPayloads;
import com.cloudresilience.modules.orders.domain.IllegalOrderTransitionException;
import com.cloudresilience.modules.orders.domain.OrderStateMachine;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderWorkflowService {
    private final JdbcTemplate jdbc;
    private final CheckoutTransactionService reservations;
    private final OutboxWriter outbox;

    public OrderWorkflowService(JdbcTemplate jdbc, CheckoutTransactionService reservations, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.reservations = reservations;
        this.outbox = outbox;
    }

    @Transactional
    public void cancelCustomerOrder(UUID customerId, UUID orderId, String reason) {
        LockedOrder order = lockOrder(customerId, orderId);
        cancel(order, customerId, reason);
    }

    @Transactional
    public void transitionAdmin(UUID adminId, UUID orderId, OrderStatus target, String reason) {
        LockedOrder order = lockOrder(null, orderId);
        if (target == OrderStatus.CANCELLED) {
            cancel(order, adminId, reason);
            return;
        }
        if (target == OrderStatus.CONFIRMED || target == OrderStatus.PENDING_PAYMENT || target == OrderStatus.PAYMENT_FAILED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment and checkout states cannot be set by an administrator");
        OrderStateMachine.requireTransition(order.status(), target);
        if (target == OrderStatus.PROCESSING) {
            if (order.paymentStatus() != PaymentStatus.SUCCEEDED)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A paid order is required before processing");
            reservations.commitForOrder(order.id(), adminId);
        }
        jdbc.update("UPDATE customer_orders SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                target.name(), order.id());
        history(order.id(), order.status(), target, adminId, cleanReason(reason));
        EventType eventType = switch (target) {
            case PROCESSING -> EventType.ORDER_PROCESSING_STARTED;
            case SHIPPED -> EventType.ORDER_SHIPPED;
            case DELIVERED -> EventType.ORDER_DELIVERED;
            default -> throw new IllegalStateException("Unsupported administrative transition event");
        };
        outbox.append(eventType, order.id(), OrderEventPayloads.transition(order.id(), order.customerId(),
                order.orderNumber(), target, order.paymentStatus(), order.itemCount(), reason), order.id(), null);
    }

    private void cancel(LockedOrder order, UUID actorId, String reason) {
        if (reason == null || reason.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is required");
        if (reason.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cancellation reason is too long");
        OrderStateMachine.requireTransition(order.status(), OrderStatus.CANCELLED);
        if (order.status() != OrderStatus.CONFIRMED || order.paymentStatus() != PaymentStatus.SUCCEEDED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only paid confirmed orders can be cancelled before processing");
        reservations.releaseForOrder(order.id(), actorId, "Order " + order.orderNumber() + " cancelled: " + reason.strip());
        int changed = jdbc.update("""
                UPDATE payments SET status='VOIDED',updated_at=CURRENT_TIMESTAMP
                WHERE order_id=? AND status='SUCCEEDED'
                """, order.id());
        if (changed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment cannot be voided");
        jdbc.update("""
                UPDATE customer_orders SET status='CANCELLED',payment_status='VOIDED',cancellation_reason=?,
                    version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?
                """, reason.strip(), order.id());
        history(order.id(), order.status(), OrderStatus.CANCELLED, actorId, reason.strip());
        outbox.append(EventType.ORDER_CANCELLED, order.id(), OrderEventPayloads.cancellation(order.id(),
                order.customerId(), order.orderNumber(), order.totalAmount(), "USD"), order.id(), null);
    }

    private LockedOrder lockOrder(UUID customerId, UUID orderId) {
        String sql = customerId == null
                ? "SELECT o.id,o.customer_id,o.order_number,o.status,o.payment_status,o.total_amount,(SELECT count(*) FROM order_items oi WHERE oi.order_id=o.id) item_count FROM customer_orders o WHERE o.id=? FOR UPDATE OF o"
                : "SELECT o.id,o.customer_id,o.order_number,o.status,o.payment_status,o.total_amount,(SELECT count(*) FROM order_items oi WHERE oi.order_id=o.id) item_count FROM customer_orders o WHERE o.id=? AND o.customer_id=? FOR UPDATE OF o";
        var rows = customerId == null
                ? jdbc.query(sql, (rs, row) -> map(rs), orderId)
                : jdbc.query(sql, (rs, row) -> map(rs), orderId, customerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return rows.getFirst();
    }

    private LockedOrder map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LockedOrder(rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class),
                rs.getString("order_number"), OrderStatus.valueOf(rs.getString("status")),
                PaymentStatus.valueOf(rs.getString("payment_status")), rs.getBigDecimal("total_amount"),
                rs.getInt("item_count"));
    }

    private void history(UUID orderId, OrderStatus from, OrderStatus to, UUID actorId, String reason) {
        jdbc.update("""
                INSERT INTO order_status_history(id,order_id,from_status,to_status,actor_user_id,reason)
                VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID(), orderId, from.name(), to.name(), actorId, reason);
    }

    private String cleanReason(String reason) { return reason == null || reason.isBlank() ? null : reason.strip(); }

    private record LockedOrder(UUID id, UUID customerId, String orderNumber, OrderStatus status,
            PaymentStatus paymentStatus, java.math.BigDecimal totalAmount, int itemCount) {}
}
