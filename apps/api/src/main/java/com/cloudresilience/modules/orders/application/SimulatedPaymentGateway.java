package com.cloudresilience.modules.orders.application;

import com.cloudresilience.modules.orders.domain.PaymentStatus;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deterministic local simulator. It never contacts a provider or charges money. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {
    public enum Scenario { SUCCESS, DECLINED, TIMEOUT, FAILURE }

    private final Scenario scenario;
    public SimulatedPaymentGateway(@Value("${app.payment.simulator.outcome:SUCCESS}") String scenario) {
        this.scenario = Scenario.valueOf(scenario.strip().toUpperCase(Locale.ROOT));
    }

    @Override
    public PaymentResult process(PaymentRequest request) {
        UUID stableReference = UUID.nameUUIDFromBytes(("cloudresilience-sim:" + request.requestId())
                .getBytes(StandardCharsets.UTF_8));
        String reference = "SIM-" + stableReference.toString().toUpperCase(Locale.ROOT);
        return switch (scenario) {
            case SUCCESS -> new PaymentResult(PaymentStatus.SUCCEEDED, reference, null);
            case DECLINED -> new PaymentResult(PaymentStatus.DECLINED, reference, "SIMULATED_DECLINE");
            case TIMEOUT -> new PaymentResult(PaymentStatus.TIMED_OUT, reference, "SIMULATED_TIMEOUT");
            case FAILURE -> new PaymentResult(PaymentStatus.FAILED, reference, "SIMULATOR_FAILURE");
        };
    }
}
