package com.cloudresilience.modules.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cloudresilience.modules.orders.domain.IllegalOrderTransitionException;
import com.cloudresilience.modules.orders.domain.OrderStateMachine;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import org.junit.jupiter.api.Test;

class OrderStateMachineTest {
    @Test
    void permitsOnlyDocumentedLifecycleEdges() {
        assertThat(OrderStateMachine.permits(OrderStatus.PENDING_PAYMENT, OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStateMachine.permits(OrderStatus.CONFIRMED, OrderStatus.PROCESSING)).isTrue();
        assertThat(OrderStateMachine.permits(OrderStatus.PROCESSING, OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStateMachine.permits(OrderStatus.SHIPPED, OrderStatus.DELIVERED)).isTrue();
        assertThat(OrderStateMachine.permits(OrderStatus.DELIVERED, OrderStatus.CANCELLED)).isFalse();
        assertThat(OrderStateMachine.permits(OrderStatus.PAYMENT_FAILED, OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStateMachine.permits(OrderStatus.CANCELLED, OrderStatus.PROCESSING)).isFalse();
    }

    @Test
    void invalidTransitionHasControlledDomainException() {
        assertThatThrownBy(() -> OrderStateMachine.requireTransition(OrderStatus.SHIPPED, OrderStatus.CANCELLED))
                .isInstanceOf(IllegalOrderTransitionException.class)
                .hasMessage("Order transition is not permitted");
    }
}
