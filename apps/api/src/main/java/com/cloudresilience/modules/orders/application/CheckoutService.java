package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.application.OrderViews.Order;
import com.cloudresilience.modules.health.CloudResilienceMetrics;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Orchestrates two short DB transactions around the no-network simulated payment boundary. */
@Service
public class CheckoutService {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
    private final CheckoutTransactionService transactions;
    private final PaymentGateway gateway;
    private final OrderQueryService queries;
    private final CloudResilienceMetrics metrics;

    public CheckoutService(CheckoutTransactionService transactions, PaymentGateway gateway, OrderQueryService queries,
            CloudResilienceMetrics metrics) {
        this.transactions = transactions;
        this.gateway = gateway;
        this.queries = queries;
        this.metrics = metrics;
    }

    public Order checkout(UUID customerId, String idempotencyKey, CheckoutRequest request) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        CheckoutTransactionService.PreparedCheckout prepared = transactions.prepare(customerId, idempotencyKey, request);
        try {
            if (prepared.paymentStatus() == PaymentStatus.PROCESSING) {
                PaymentGateway.PaymentResult result;
                try {
                    result = gateway.process(new PaymentGateway.PaymentRequest(prepared.requestId(), prepared.orderNumber(),
                            prepared.amount(), prepared.currency()));
                } catch (RuntimeException simulatorFailure) {
                    result = new PaymentGateway.PaymentResult(PaymentStatus.FAILED,
                            "SIM-" + prepared.requestId().toString().toUpperCase(java.util.Locale.ROOT), "SIMULATOR_FAILURE");
                }
                transactions.completePayment(prepared.paymentId(), result);
            }
            Order order = queries.customerOrder(customerId, prepared.orderId());
            metrics.recordCheckout(order.paymentStatus() == PaymentStatus.SUCCEEDED);
            return order;
        } catch (RuntimeException failure) {
            metrics.recordCheckout(false);
            throw failure;
        }
    }
}
