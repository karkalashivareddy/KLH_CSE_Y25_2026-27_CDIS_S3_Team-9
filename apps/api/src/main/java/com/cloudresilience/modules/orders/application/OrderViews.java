package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderViews {
    private OrderViews() {}
    public record Item(UUID itemId, UUID productId, String sku, String productName, BigDecimal unitPrice,
            int quantity, BigDecimal lineTotal, Instant createdAt) {}
    public record History(UUID eventId, OrderStatus fromStatus, OrderStatus toStatus, String reason, Instant createdAt) {}
    public record Order(UUID id, String orderNumber, OrderStatus status, PaymentStatus paymentStatus,
            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal shippingAmount,
            BigDecimal totalAmount, String currency, String cancellationReason, Instant createdAt, Instant updatedAt,
            List<Item> items, List<History> history) {}
    public record Page<T>(List<T> content, int page, int size, long totalElements, int totalPages) {}
    public record Payment(UUID id, PaymentStatus status, BigDecimal amount, String currency,
            String failureCategory, boolean simulated, Instant createdAt, Instant updatedAt) {}
    public record AdminSummary(long totalOrders, Map<String, Long> byStatus) {}
}
