package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Provider-neutral payment boundary; this phase only binds it to a no-money simulator. */
public interface PaymentGateway {
    PaymentResult process(PaymentRequest request);

    record PaymentRequest(UUID requestId, String orderNumber, BigDecimal amount, String currency) {}
    record PaymentResult(PaymentStatus status, String providerReference, String failureCategory) {}
}
