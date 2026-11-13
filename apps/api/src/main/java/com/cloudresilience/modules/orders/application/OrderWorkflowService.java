package com.cloudresilience.modules.orders.application;

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

    public OrderWorkflowService(JdbcTemplate jdbc, CheckoutTransactionService reservations) {
        this.jdbc = jdbc;
        this.reservations = reservations;
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
    }

    private LockedOrder lockOrder(UUID customerId, UUID orderId) {
        String sql = customerId == null
                ? "SELECT id,order_number,status,payment_status FROM customer_orders WHERE id=? FOR UPDATE"
                : "SELECT id,order_number,status,payment_status FROM customer_orders WHERE id=? AND customer_id=? FOR UPDATE";
        var rows = customerId == null
                ? jdbc.query(sql, (rs, row) -> new LockedOrder(rs.getObject("id", UUID.class), rs.getString("order_number"),
                        OrderStatus.valueOf(rs.getString("status")), PaymentStatus.valueOf(rs.getString("payment_status"))), orderId)
                : jdbc.query(sql, (rs, row) -> new LockedOrder(rs.getObject("id", UUID.class), rs.getString("order_number"),
                        OrderStatus.valueOf(rs.getString("status")), PaymentStatus.valueOf(rs.getString("payment_status"))), orderId, customerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return rows.getFirst();
    }

    private void history(UUID orderId, OrderStatus from, OrderStatus to, UUID actorId, String reason) {
        jdbc.update("""
                INSERT INTO order_status_history(id,order_id,from_status,to_status,actor_user_id,reason)
                VALUES(?,?,?,?,?,?)
                """, UUID.randomUUID(), orderId, from.name(), to.name(), actorId, reason);
    }

    private String cleanReason(String reason) { return reason == null || reason.isBlank() ? null : reason.strip(); }

    private record LockedOrder(UUID id, String orderNumber, OrderStatus status, PaymentStatus paymentStatus) {}
}
