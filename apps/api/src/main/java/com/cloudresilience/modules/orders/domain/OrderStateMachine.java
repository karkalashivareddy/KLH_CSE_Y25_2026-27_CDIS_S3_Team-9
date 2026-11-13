package com.cloudresilience.modules.orders.domain;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class OrderStateMachine {
    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            OrderStatus.PENDING_PAYMENT, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.PAYMENT_FAILED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, EnumSet.of(OrderStatus.SHIPPED),
            OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class));

    private OrderStateMachine() {}

    public static boolean permits(OrderStatus from, OrderStatus to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(OrderStatus from, OrderStatus to) {
        if (!permits(from, to)) throw new IllegalOrderTransitionException(from, to);
    }
}
