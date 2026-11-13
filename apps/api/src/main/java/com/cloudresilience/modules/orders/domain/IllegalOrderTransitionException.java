package com.cloudresilience.modules.orders.domain;

public final class IllegalOrderTransitionException extends RuntimeException {
    private final OrderStatus from;
    private final OrderStatus to;

    public IllegalOrderTransitionException(OrderStatus from, OrderStatus to) {
        super("Order transition is not permitted");
        this.from = from;
        this.to = to;
    }

    public OrderStatus from() { return from; }
    public OrderStatus to() { return to; }
}
