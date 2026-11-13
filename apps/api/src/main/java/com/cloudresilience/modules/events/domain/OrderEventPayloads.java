package com.cloudresilience.modules.events.domain;

import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Builds explicit, data-minimised payloads for order lifecycle events. */
public final class OrderEventPayloads {
    private OrderEventPayloads() {}

    public static Map<String, Object> order(UUID orderId, UUID customerId, String orderNumber, OrderStatus status,
            PaymentStatus paymentStatus, BigDecimal totalAmount, String currency, int itemCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("orderNumber", orderNumber);
        payload.put("status", status.name());
        payload.put("paymentStatus", paymentStatus.name());
        payload.put("totalAmount", totalAmount.toPlainString());
        payload.put("currency", currency);
        payload.put("itemCount", itemCount);
        return payload;
    }

    public static Map<String, Object> cancellation(UUID orderId, UUID customerId, String orderNumber,
            BigDecimal totalAmount, String currency) {
        return order(orderId, customerId, orderNumber, OrderStatus.CANCELLED,
                PaymentStatus.VOIDED, totalAmount, currency, 0);
    }

    public static Map<String, Object> transition(UUID orderId, UUID customerId, String orderNumber,
            OrderStatus status, PaymentStatus paymentStatus, int itemCount, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("customerId", customerId.toString());
        payload.put("orderNumber", orderNumber);
        payload.put("status", status.name());
        payload.put("paymentStatus", paymentStatus.name());
        payload.put("currency", "USD");
        payload.put("itemCount", itemCount);
        if (reason != null && !reason.isBlank()) payload.put("reason", truncate(reason, 200));
        return payload;
    }

    private static String truncate(String value, int max) {
        String stripped = value.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max);
    }
}
