package com.cloudresilience.modules.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.cloudresilience.modules.events.persistence.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;

class CloudResilienceMetricsTest {
    @Test
    void businessMetricsUseOnlyBoundedLabels() {
        var registry = new SimpleMeterRegistry();
        var metrics = new CloudResilienceMetrics(registry, mock(OutboxRepository.class), mock(AmqpAdmin.class));

        metrics.recordCheckout(true);
        metrics.recordOrderTransition("SHIPPED");
        var sample = metrics.startConsumerProcessing();
        metrics.finishConsumerProcessing(sample, "order-activity", false);

        assertThat(registry.get("cloudresilience.checkout.attempts").tag("outcome", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("cloudresilience.order.transitions").tag("to_status", "SHIPPED").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("cloudresilience.event.consumer.retries").tag("consumer", "order-activity").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("cloudresilience.event.consumer.processing")
                .tag("consumer", "order-activity").tag("outcome", "retry").timer().count()).isEqualTo(1);
        assertThat(registry.getMeters()).noneMatch(meter -> meter.getId().getTags().stream()
                .anyMatch(tag -> tag.getKey().contains("id") || tag.getKey().contains("email")));
    }
}
