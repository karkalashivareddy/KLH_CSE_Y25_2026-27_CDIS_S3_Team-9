package com.cloudresilience.modules.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudresilience.modules.orders.application.PaymentGateway;
import com.cloudresilience.modules.orders.application.SimulatedPaymentGateway;
import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimulatedPaymentGatewayTest {
    @ParameterizedTest
    @CsvSource({"SUCCESS,SUCCEEDED,", "DECLINED,DECLINED,SIMULATED_DECLINE", "TIMEOUT,TIMED_OUT,SIMULATED_TIMEOUT", "FAILURE,FAILED,SIMULATOR_FAILURE"})
    void supportsDeterministicScenariosAndStableDuplicateRequest(String scenario, PaymentStatus status, String failure) {
        var gateway = new SimulatedPaymentGateway(scenario);
        UUID requestId = UUID.randomUUID();
        var request = new PaymentGateway.PaymentRequest(requestId, "CR-TEST", new BigDecimal("14.25"), "USD");
        var first = gateway.process(request);
        var retry = gateway.process(request);
        assertThat(first.status()).isEqualTo(status);
        assertThat(first.failureCategory()).isEqualTo(failure == null || failure.isBlank() ? null : failure);
        assertThat(retry).isEqualTo(first);
        assertThat(first.providerReference()).startsWith("SIM-");
    }
}
