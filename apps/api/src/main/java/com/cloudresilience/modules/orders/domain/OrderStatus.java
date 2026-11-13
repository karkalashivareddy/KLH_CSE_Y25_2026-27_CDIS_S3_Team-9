package com.cloudresilience.modules.orders.domain;

public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED
}
