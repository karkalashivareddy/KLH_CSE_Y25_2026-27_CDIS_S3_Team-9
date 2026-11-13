package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.api.CheckoutRequest;
import com.cloudresilience.modules.orders.application.OrderViews.Order;
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

    public CheckoutService(CheckoutTransactionService transactions, PaymentGateway gateway, OrderQueryService queries) {
        this.transactions = transactions;
        this.gateway = gateway;
        this.queries = queries;
    }

    public Order checkout(UUID customerId, String idempotencyKey, CheckoutRequest request) {
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key header is required");
        CheckoutTransactionService.PreparedCheckout prepared = transactions.prepare(customerId, idempotencyKey, request);
        if (prepared.paymentStatus() == PaymentStatus.PROCESSING) {
            PaymentGateway.PaymentResult result;
            try {
                result = gateway.process(new PaymentGateway.PaymentRequest(prepared.requestId(), prepared.orderNumber(),
                        prepared.amount(), prepared.currency()));
            } catch (RuntimeException simulatorFailure) {
                // The current adapter has no external side effect; persist a safe deterministic failure.
                result = new PaymentGateway.PaymentResult(PaymentStatus.FAILED,
                        "SIM-" + prepared.requestId().toString().toUpperCase(java.util.Locale.ROOT), "SIMULATOR_FAILURE");
            }
            transactions.completePayment(prepared.paymentId(), result);
        }
        return queries.customerOrder(customerId, prepared.orderId());
    }
}
