package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.events.application.OutboxWriter;
import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.domain.OrderEventPayloads;
import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Database transaction boundaries for checkout creation and payment finalization. */
@Service
public class CheckoutTransactionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final PricingCalculator pricing;
    private final OutboxWriter outbox;

    public CheckoutTransactionService(JdbcTemplate jdbc, PricingCalculator pricing, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.pricing = pricing;
        this.outbox = outbox;
    }

    public record PreparedCheckout(UUID orderId, UUID paymentId, UUID requestId, String orderNumber,
            BigDecimal amount, String currency, OrderStatus orderStatus, PaymentStatus paymentStatus) {}

    @Transactional
    public PreparedCheckout prepare(UUID customerId, String idempotencyKey, CheckoutRequest request) {
        Map<UUID, Integer> expected = canonicalItems(request);
        String requestHash = fingerprint(expected);
        jdbc.update("""
                INSERT INTO checkout_idempotency(id,customer_id,idempotency_key,request_hash,order_id)
                VALUES(?,?,?,?,NULL) ON CONFLICT (customer_id,idempotency_key) DO NOTHING
                """, UUID.randomUUID(), customerId, idempotencyKey, requestHash);
        var claims = jdbc.query("""
                SELECT id,request_hash,order_id FROM checkout_idempotency
                WHERE customer_id=? AND idempotency_key=? FOR UPDATE
                """, (rs, row) -> new Claim(rs.getObject("id", UUID.class), rs.getString("request_hash"),
                rs.getObject("order_id", UUID.class)), customerId, idempotencyKey);
        if (claims.isEmpty()) throw new IllegalStateException("Idempotency claim disappeared");
        Claim claim = claims.getFirst();
        if (!MessageDigest.isEqual(claim.requestHash().getBytes(StandardCharsets.US_ASCII), requestHash.getBytes(StandardCharsets.US_ASCII)))
            throw conflict("Idempotency key was already used with a different cart payload");
        if (claim.orderId() != null) return existing(claim.orderId());

        UUID cartId = lockActiveCart(customerId);
        List<CartLine> cartLines = loadCurrentCart(cartId);
        if (cartLines.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        Map<UUID, Integer> actual = new LinkedHashMap<>();
        cartLines.forEach(line -> actual.put(line.productId(), line.quantity()));
        if (!actual.equals(expected)) throw conflict("Cart changed; review the current cart before checkout");

        var pending = jdbc.query("SELECT id FROM customer_orders WHERE cart_id=? AND status='PENDING_PAYMENT' LIMIT 1",
                (rs, row) -> rs.getObject(1, UUID.class), cartId);
        if (!pending.isEmpty()) throw conflict("A checkout for this cart is already being processed");

        // Product/category locks prevent deactivation or repricing until the snapshot transaction commits.
        for (CartLine line : cartLines) {
            List<UUID> active = jdbc.query("""
                    SELECT p.id FROM products p JOIN categories c ON c.id=p.category_id
                    WHERE p.id=? AND p.status='ACTIVE' AND c.active FOR SHARE OF p,c
                    """, (rs, row) -> rs.getObject(1, UUID.class), line.productId());
            if (active.isEmpty()) throw conflict("A cart product is no longer available");
        }

        List<PricingCalculator.PricedLine> priced = pricing.price(cartLines.stream()
                .map(line -> new PricingCalculator.PriceInput(line.unitPrice(), line.quantity())).toList());
        PricingCalculator.Totals totals = pricing.totals(priced);
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID paymentRequestId = UUID.randomUUID();
        String orderNumber = newOrderNumber();
        jdbc.update("""
                INSERT INTO customer_orders(id,order_number,customer_id,cart_id,status,payment_status,subtotal,
                    discount_amount,tax_amount,shipping_amount,total_amount,currency,idempotency_key,request_hash)
                VALUES(?,?,?,?,'PENDING_PAYMENT','PROCESSING',?,?,?,?,?,'USD',?,?)
                """, orderId, orderNumber, customerId, cartId, totals.subtotal(), totals.discount(), totals.tax(),
                totals.shipping(), totals.total(), idempotencyKey, requestHash);

        List<PricingCalculator.PricedLine> priceLines = priced;
        for (int index = 0; index < cartLines.size(); index++) {
            CartLine line = cartLines.get(index);
            PricingCalculator.PricedLine amount = priceLines.get(index);
            reserve(line, orderId, customerId);
            jdbc.update("""
                    INSERT INTO order_items(id,order_id,product_id,sku_snapshot,product_name_snapshot,unit_price,quantity,line_total)
                    VALUES(?,?,?,?,?,?,?,?)
                    """, UUID.randomUUID(), orderId, line.productId(), line.sku(), line.name(), amount.unitPrice(),
                    amount.quantity(), amount.lineTotal());
        }
        jdbc.update("""
                INSERT INTO payments(id,order_id,request_id,provider,status,amount,currency)
                VALUES(?,?,?,'SIMULATED','PROCESSING',?,'USD')
                """, paymentId, orderId, paymentRequestId, totals.total());
        jdbc.update("""
                INSERT INTO order_status_history(id,order_id,from_status,to_status,actor_user_id,reason)
                VALUES(?,?,NULL,'PENDING_PAYMENT',?,'Checkout submitted; simulated payment processing')
                """, UUID.randomUUID(), orderId, customerId);
        jdbc.update("UPDATE checkout_idempotency SET order_id=? WHERE id=? AND order_id IS NULL", orderId, claim.id());
        return new PreparedCheckout(orderId, paymentId, paymentRequestId, orderNumber, totals.total(), "USD",
                OrderStatus.PENDING_PAYMENT, PaymentStatus.PROCESSING);
    }

    @Transactional
    public void completePayment(UUID paymentId, PaymentGateway.PaymentResult result) {
        var payments = jdbc.query("""
                SELECT id,order_id,request_id,status FROM payments WHERE id=? FOR UPDATE
                """, (rs, row) -> new PaymentRow(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getObject("request_id", UUID.class), PaymentStatus.valueOf(rs.getString("status"))), paymentId);
        if (payments.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        PaymentRow payment = payments.getFirst();
        if (payment.status() != PaymentStatus.PROCESSING) return;
        var orders = jdbc.query("""
                SELECT o.id,o.customer_id,o.cart_id,o.status,o.payment_status,o.order_number,o.total_amount,o.currency,
                       (SELECT count(*) FROM order_items oi WHERE oi.order_id=o.id) item_count
                FROM customer_orders o WHERE o.id=? FOR UPDATE
                """, (rs, row) -> new LockedOrder(rs.getObject("id", UUID.class), rs.getObject("customer_id", UUID.class),
                rs.getObject("cart_id", UUID.class), OrderStatus.valueOf(rs.getString("status")),
                PaymentStatus.valueOf(rs.getString("payment_status")), rs.getString("order_number"),
                rs.getBigDecimal("total_amount"), rs.getString("currency").trim(), rs.getInt("item_count")),
                payment.orderId());
        if (orders.isEmpty()) throw new IllegalStateException("Payment order is missing");
        LockedOrder order = orders.getFirst();
        if (order.status() != OrderStatus.PENDING_PAYMENT || order.paymentStatus() != PaymentStatus.PROCESSING) return;
        if (result.status() != PaymentStatus.SUCCEEDED && result.status() != PaymentStatus.DECLINED
                && result.status() != PaymentStatus.TIMED_OUT && result.status() != PaymentStatus.FAILED)
            throw new IllegalArgumentException("Unsupported simulator result");
        jdbc.update("UPDATE payments SET status=?,provider_reference=?,failure_category=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                result.status().name(), result.providerReference(), result.failureCategory(), paymentId);
        if (result.status() == PaymentStatus.SUCCEEDED) {
            jdbc.update("UPDATE customer_orders SET status='CONFIRMED',payment_status='SUCCEEDED',version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    order.id());
            history(order.id(), OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED, order.customerId(), "Simulated payment succeeded");
            removePurchasedCartLines(order);
            outbox.append(EventType.ORDER_PLACED, order.id(), OrderEventPayloads.order(order.id(), order.customerId(),
                    order.orderNumber(), OrderStatus.CONFIRMED, PaymentStatus.SUCCEEDED, order.totalAmount(),
                    order.currency(), order.itemCount()), order.id(), null);
        } else {
            releaseReservations(order.id(), order.customerId(), "Order payment failed");
            jdbc.update("UPDATE customer_orders SET status='PAYMENT_FAILED',payment_status=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=?",
                    result.status().name(), order.id());
            history(order.id(), OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED, order.customerId(),
                    "Simulated payment " + result.status().name().toLowerCase(java.util.Locale.ROOT));
            outbox.append(EventType.ORDER_PAYMENT_FAILED, order.id(), OrderEventPayloads.order(order.id(),
                    order.customerId(), order.orderNumber(), OrderStatus.PAYMENT_FAILED, result.status(),
                    order.totalAmount(), order.currency(), order.itemCount()), order.id(), null);
        }
    }

    @Transactional
    public void releaseForOrder(UUID orderId, UUID actorId, String reason) {
        releaseReservations(orderId, actorId, reason);
    }

    @Transactional
    public void commitForOrder(UUID orderId, UUID actorId) {
        commitReservations(orderId, actorId);
    }

    private PreparedCheckout existing(UUID orderId) {
        var rows = jdbc.query("""
                SELECT o.id order_id,o.status order_status,o.payment_status,o.order_number,o.total_amount,p.id payment_id,
                       p.request_id,p.status payment_state
                FROM customer_orders o JOIN payments p ON p.order_id=o.id WHERE o.id=?
                """, (rs, row) -> new PreparedCheckout(rs.getObject("order_id", UUID.class), rs.getObject("payment_id", UUID.class),
                rs.getObject("request_id", UUID.class), rs.getString("order_number"), rs.getBigDecimal("total_amount"), "USD",
                OrderStatus.valueOf(rs.getString("order_status")), PaymentStatus.valueOf(rs.getString("payment_state"))), orderId);
        if (rows.isEmpty()) throw new IllegalStateException("Idempotent order or payment is missing");
        return rows.getFirst();
    }

    private UUID lockActiveCart(UUID customerId) {
        var rows = jdbc.query("SELECT id FROM carts WHERE user_id=? AND status='ACTIVE' FOR UPDATE",
                (rs, row) -> rs.getObject(1, UUID.class), customerId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
        return rows.getFirst();
    }

    private List<CartLine> loadCurrentCart(UUID cartId) {
        return jdbc.query("""
                SELECT ci.product_id,ci.quantity,p.sku,p.name,p.price,p.currency,p.status,c.active
                FROM cart_items ci JOIN products p ON p.id=ci.product_id JOIN categories c ON c.id=p.category_id
                WHERE ci.cart_id=? ORDER BY ci.product_id
                """, (rs, row) -> new CartLine(rs.getObject("product_id", UUID.class), rs.getInt("quantity"),
                rs.getString("sku"), rs.getString("name"), rs.getBigDecimal("price"), rs.getString("currency").trim(),
                rs.getString("status"), rs.getBoolean("active")), cartId);
    }

    private void reserve(CartLine line, UUID orderId, UUID customerId) {
        var rows = jdbc.query("SELECT quantity_on_hand,quantity_reserved FROM inventory WHERE product_id=? FOR UPDATE",
                (rs, row) -> new InventoryRow(rs.getLong("quantity_on_hand"), rs.getLong("quantity_reserved")), line.productId());
        if (rows.isEmpty()) throw conflict("Inventory is not initialized for a cart product");
        InventoryRow current = rows.getFirst();
        if (current.onHand() - current.reserved() < line.quantity()) throw conflict("Insufficient available inventory");
        int changed = jdbc.update("UPDATE inventory SET quantity_reserved=quantity_reserved+?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND quantity_on_hand-quantity_reserved>=?",
                line.quantity(), line.productId(), line.quantity());
        if (changed != 1) throw conflict("Insufficient available inventory");
        UUID reservationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_reservations(id,product_id,quantity,status,created_by,order_id)
                VALUES(?,?,?,'RESERVED',?,?)
                """, reservationId, line.productId(), line.quantity(), customerId, orderId);
        movement(line.productId(), "ORDER_RESERVATION", 0, line.quantity(), current.onHand(), current.onHand(),
                current.reserved(), current.reserved() + line.quantity(), "Order " + orderId + " inventory reserved", customerId, reservationId);
    }

    private void releaseReservations(UUID orderId, UUID actorId, String reason) {
        List<ReservationRow> reservations = jdbc.query("""
                SELECT id,product_id,quantity,status FROM inventory_reservations
                WHERE order_id=? ORDER BY product_id FOR UPDATE
                """, (rs, row) -> new ReservationRow(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getLong("quantity"), rs.getString("status")), orderId);
        for (ReservationRow reservation : reservations) {
            if (!"RESERVED".equals(reservation.status())) continue;
            InventoryRow inventory = lockInventory(reservation.productId());
            if (inventory.reserved() < reservation.quantity()) throw new IllegalStateException("Reservation exceeds reserved inventory");
            jdbc.update("UPDATE inventory SET quantity_reserved=quantity_reserved-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",
                    reservation.quantity(), reservation.productId());
            jdbc.update("UPDATE inventory_reservations SET status='RELEASED',released_at=CURRENT_TIMESTAMP WHERE id=? AND status='RESERVED'",
                    reservation.id());
            movement(reservation.productId(), "ORDER_RESERVATION_RELEASE", 0, -reservation.quantity(), inventory.onHand(),
                    inventory.onHand(), inventory.reserved(), inventory.reserved() - reservation.quantity(), reason, actorId, reservation.id());
        }
    }

    private void commitReservations(UUID orderId, UUID actorId) {
        List<ReservationRow> reservations = jdbc.query("""
                SELECT id,product_id,quantity,status FROM inventory_reservations
                WHERE order_id=? ORDER BY product_id FOR UPDATE
                """, (rs, row) -> new ReservationRow(rs.getObject("id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getLong("quantity"), rs.getString("status")), orderId);
        if (reservations.isEmpty()) throw conflict("Order has no active inventory reservations");
        for (ReservationRow reservation : reservations) {
            if (!"RESERVED".equals(reservation.status())) throw conflict("Order inventory is no longer reserved");
            InventoryRow inventory = lockInventory(reservation.productId());
            if (inventory.reserved() < reservation.quantity() || inventory.onHand() < reservation.quantity())
                throw new IllegalStateException("Reservation exceeds inventory");
            jdbc.update("UPDATE inventory SET quantity_on_hand=quantity_on_hand-?,quantity_reserved=quantity_reserved-?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",
                    reservation.quantity(), reservation.quantity(), reservation.productId());
            jdbc.update("UPDATE inventory_reservations SET status='COMMITTED',committed_at=CURRENT_TIMESTAMP WHERE id=? AND status='RESERVED'",
                    reservation.id());
            movement(reservation.productId(), "ORDER_COMMIT", -reservation.quantity(), -reservation.quantity(),
                    inventory.onHand(), inventory.onHand() - reservation.quantity(), inventory.reserved(),
                    inventory.reserved() - reservation.quantity(), "Order " + orderId + " moved to processing", actorId, reservation.id());
        }
    }

    private InventoryRow lockInventory(UUID productId) {
        var rows = jdbc.query("SELECT quantity_on_hand,quantity_reserved FROM inventory WHERE product_id=? FOR UPDATE",
                (rs, row) -> new InventoryRow(rs.getLong("quantity_on_hand"), rs.getLong("quantity_reserved")), productId);
        if (rows.isEmpty()) throw new IllegalStateException("Inventory row disappeared");
        return rows.getFirst();
    }

    private void removePurchasedCartLines(LockedOrder order) {
        if (order.cartId() == null) return;
        jdbc.query("SELECT id FROM carts WHERE id=? FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class), order.cartId());
        jdbc.update("""
                DELETE FROM cart_items ci USING order_items oi
                WHERE oi.order_id=? AND ci.cart_id=? AND ci.product_id=oi.product_id AND ci.quantity=oi.quantity
                """, order.id(), order.cartId());
        jdbc.update("UPDATE carts SET updated_at=CURRENT_TIMESTAMP WHERE id=?", order.cartId());
    }

    private void movement(UUID productId, String type, long quantityDelta, long reservedDelta,
            long previousQuantity, long newQuantity, long previousReserved, long newReserved,
            String reason, UUID actor, UUID reference) {
        jdbc.update("""
                INSERT INTO inventory_movements(id,product_id,movement_type,quantity_delta,reserved_delta,
                    previous_quantity,new_quantity,previous_reserved,new_reserved,reason,reference_id,performed_by)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(), productId, type, quantityDelta, reservedDelta, previousQuantity, newQuantity,
                previousReserved, newReserved, reason, reference, actor);
    }

    private void history(UUID orderId, OrderStatus from, OrderStatus to, UUID actor, String reason) {
        jdbc.update("INSERT INTO order_status_history(id,order_id,from_status,to_status,actor_user_id,reason) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), orderId, from.name(), to.name(), actor, reason);
    }

    private Map<UUID, Integer> canonicalItems(CheckoutRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout requires cart line assertions");
        Map<UUID, Integer> values = new LinkedHashMap<>();
        request.items().stream().sorted(Comparator.comparing(line -> line.productId().toString())).forEach(line -> {
            if (values.putIfAbsent(line.productId(), line.quantity()) != null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout contains a duplicate product");
        });
        return values;
    }

    private String fingerprint(Map<UUID, Integer> items) {
        String canonical = items.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue()).collect(java.util.stream.Collectors.joining("\n"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private String newOrderNumber() {
        byte[] bytes = new byte[12];
        RANDOM.nextBytes(bytes);
        return "CR-" + HexFormat.of().withUpperCase().formatHex(bytes);
    }

    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Claim(UUID id, String requestHash, UUID orderId) {}
    private record CartLine(UUID productId, int quantity, String sku, String name, BigDecimal unitPrice,
            String currency, String status, boolean categoryActive) {}
    private record InventoryRow(long onHand, long reserved) {}
    private record ReservationRow(UUID id, UUID productId, long quantity, String status) {}
    private record PaymentRow(UUID id, UUID orderId, UUID requestId, PaymentStatus status) {}
    private record LockedOrder(UUID id, UUID customerId, UUID cartId, OrderStatus status, PaymentStatus paymentStatus,
            String orderNumber, BigDecimal totalAmount, String currency, int itemCount) {}
}
