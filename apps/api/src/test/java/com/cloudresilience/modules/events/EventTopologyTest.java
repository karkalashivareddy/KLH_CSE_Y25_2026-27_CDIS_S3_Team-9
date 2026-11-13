package com.cloudresilience.modules.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.cloudresilience.modules.events.domain.EventType;
import com.cloudresilience.modules.events.messaging.EventTopology;
import org.junit.jupiter.api.Test;

class EventTopologyTest {
    private final EventTopology topology = new EventTopology("100,200,400");

    @Test
    void mapsEachEventTypeToAStableRoutingKey() {
        assertThat(EventType.ORDER_PLACED.routingKey()).isEqualTo("order.placed");
        assertThat(EventType.ORDER_PAYMENT_FAILED.routingKey()).isEqualTo("order.payment.failed");
        assertThat(EventType.fromWireName("OrderDelivered")).isEqualTo(EventType.ORDER_DELIVERED);
    }

    @Test
    void retryRoutingUsesTheConfiguredTierAndClampsAtTheLastTier() {
        assertThat(topology.retryQueueName("notifications", 1)).endsWith("retry.100ms");
        assertThat(topology.retryQueueName("notifications", 2)).endsWith("retry.200ms");
        assertThat(topology.retryQueueName("notifications", 3)).endsWith("retry.400ms");
        assertThat(topology.retryQueueName("notifications", 9)).endsWith("retry.400ms");
        assertThat(topology.retryRoutingKey("activity", 1)).isEqualTo("activity.0");
        assertThat(topology.retryRoutingKey("activity", 9)).isEqualTo("activity.2");
        assertThat(topology.retryQueueNames("activity")).hasSize(3);
    }

    @Test
    void definesBoundedRetriesAndDurableDeadLetterRouting() {
        assertThat(EventTopology.MAX_RETRIES).isEqualTo(3);
        assertThat(EventTopology.EVENTS_EXCHANGE).isEqualTo("cloudresilience.events");
        assertThat(EventTopology.DEAD_LETTER_EXCHANGE).isEqualTo("cloudresilience.events.dlx");
    }
}
