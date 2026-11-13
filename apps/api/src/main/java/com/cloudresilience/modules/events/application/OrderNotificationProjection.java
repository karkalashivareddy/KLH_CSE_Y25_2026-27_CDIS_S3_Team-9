package com.cloudresilience.modules.events.application;

import com.cloudresilience.modules.events.domain.EventEnvelope;
import com.cloudresilience.modules.events.domain.EventType;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists an in-app notification for each order lifecycle event. This is the
 * durable effect of the notification consumer; it does not call an external
 * email or push provider and makes no delivery claim.
 */
@Service
public class OrderNotificationProjection {
    private final JdbcTemplate jdbc;

    public OrderNotificationProjection(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void project(EventEnvelope envelope) {
        Map<String, Object> payload = envelope.payload();
        UUID customerId = uuid(payload.get("customerId"), "customerId");
        UUID orderId = uuid(payload.get("orderId"), "orderId");
        String orderNumber = text(payload.get("orderNumber"), "orderNumber");
        EventType type = EventType.fromWireName(envelope.eventType());
        jdbc.update("""
                INSERT INTO in_app_notifications(id,customer_id,order_id,event_id,event_type,title,body)
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT (event_id, customer_id) DO NOTHING
                """, UUID.randomUUID(), customerId, orderId, envelope.eventId(), type.wireName(),
                title(type, orderNumber), body(type, payload, orderNumber));
    }

    private String title(EventType type, String orderNumber) {
        return switch (type) {
            case ORDER_PLACED, ORDER_PAYMENT_SUCCEEDED -> "Order confirmed";
            case ORDER_PAYMENT_FAILED -> "Payment failed";
            case ORDER_CANCELLED -> "Order cancelled";
            case ORDER_PROCESSING_STARTED -> "Order processing started";
            case ORDER_SHIPPED -> "Order shipped";
            case ORDER_DELIVERED -> "Order delivered";
        };
    }

    private String body(EventType type, Map<String, Object> payload, String orderNumber) {
        String amount = payload.get("totalAmount") == null ? "" : " Total $" + payload.get("totalAmount") + ".";
        return switch (type) {
            case ORDER_PLACED, ORDER_PAYMENT_SUCCEEDED ->
                    "Order " + orderNumber + " was confirmed with simulated payment." + amount;
            case ORDER_PAYMENT_FAILED -> "Payment for order " + orderNumber
                    + " did not succeed. The inventory reservation was released.";
            case ORDER_CANCELLED -> "Order " + orderNumber + " was cancelled and the inventory reservation was released.";
            case ORDER_PROCESSING_STARTED -> "Order " + orderNumber + " is being prepared for fulfilment.";
            case ORDER_SHIPPED -> "Order " + orderNumber + " has been shipped.";
            case ORDER_DELIVERED -> "Order " + orderNumber + " has been delivered.";
        };
    }

    private UUID uuid(Object value, String field) {
        if (value == null) throw new IllegalArgumentException("Event payload is missing " + field);
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Event payload has an invalid " + field, failure);
        }
    }

    private String text(Object value, String field) {
        if (value == null) throw new IllegalArgumentException("Event payload is missing " + field);
        String text = value.toString().strip();
        if (text.isEmpty() || text.length() > 32) throw new IllegalArgumentException("Invalid " + field);
        return text.toUpperCase(Locale.ROOT);
    }
}
