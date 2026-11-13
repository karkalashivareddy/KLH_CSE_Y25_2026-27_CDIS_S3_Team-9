package com.cloudresilience.modules.events.domain;

import java.util.Arrays;

/** Versioned, allow-listed domain event types published from order transactions. */
public enum EventType {
    ORDER_PLACED("OrderPlaced", "order.placed"),
    ORDER_PAYMENT_SUCCEEDED("OrderPaymentSucceeded", "order.payment.succeeded"),
    ORDER_PAYMENT_FAILED("OrderPaymentFailed", "order.payment.failed"),
    ORDER_CANCELLED("OrderCancelled", "order.cancelled"),
    ORDER_PROCESSING_STARTED("OrderProcessingStarted", "order.processing.started"),
    ORDER_SHIPPED("OrderShipped", "order.shipped"),
    ORDER_DELIVERED("OrderDelivered", "order.delivered");

    public static final int CURRENT_VERSION = 1;

    private final String wireName;
    private final String routingKey;

    EventType(String wireName, String routingKey) {
        this.wireName = wireName;
        this.routingKey = routingKey;
    }

    public String wireName() { return wireName; }

    public String routingKey() { return routingKey; }

    public static EventType fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown event type"));
    }
}
