package com.cloudresilience.modules.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cloudresilience.modules.events.application.OutboxPublisher;
import com.cloudresilience.modules.events.messaging.EventPublisher;
import com.cloudresilience.modules.events.persistence.OutboxEvent;
import com.cloudresilience.modules.events.persistence.OutboxRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxPublisherTest {
    private final OutboxRepository repository = mock(OutboxRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);

    private OutboxPublisher publisher(boolean enabled) {
        return new OutboxPublisher(repository, publisher, enabled, 10, 30, 5, 2, 60);
    }

    @Test
    void marksPublishedOnlyAfterTheBrokerConfirms() {
        OutboxEvent event = event("OrderPlaced");
        when(repository.claimDue(anyInt(), anyString(), anyInt())).thenReturn(List.of(event));
        when(repository.markPublished(eq(event.id()), anyString())).thenReturn(true);

        assertThat(publisher(true).publishDueOnce()).isEqualTo(1);

        verify(publisher).publish(event, "order.placed");
        verify(repository).markPublished(eq(event.id()), anyString());
    }

    @Test
    void failedPublicationIsRescheduledWithABoundedCategory() {
        OutboxEvent event = event("OrderShipped");
        when(repository.claimDue(anyInt(), anyString(), anyInt())).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new EventPublisher.EventPublishException("Publisher confirm failed"))
                .when(publisher).publish(any(), anyString());

        assertThat(publisher(true).publishDueOnce()).isZero();

        ArgumentCaptor<Integer> backoff = ArgumentCaptor.forClass(Integer.class);
        verify(repository).markFailed(eq(event.id()), anyString(), eq("CONFIRM_TIMEOUT"), backoff.capture(), eq(5));
        assertThat(backoff.getValue()).isEqualTo(2);
        verify(repository, never()).markPublished(any(), anyString());
    }

    @Test
    void disabledPublisherDoesNotClaimEvents() {
        assertThat(publisher(false).publishDueOnce()).isZero();
        verify(repository, never()).claimDue(anyInt(), anyString(), anyInt());
    }

    @Test
    void unroutableMessagesAreNotTreatedAsPublished() {
        OutboxEvent event = event("OrderCancelled");
        when(repository.claimDue(anyInt(), anyString(), anyInt())).thenReturn(List.of(event));
        org.mockito.Mockito.doThrow(new EventPublisher.EventPublishException("Event was not routable"))
                .when(publisher).publish(any(), anyString());

        publisher(true).publishDueOnce();

        verify(repository).markFailed(eq(event.id()), anyString(), eq("UNROUTABLE"), anyInt(), eq(5));
    }

    private OutboxEvent event(String type) {
        return new OutboxEvent(UUID.randomUUID(), UUID.randomUUID(), type, 1, "{}", 0, UUID.randomUUID());
    }
}
