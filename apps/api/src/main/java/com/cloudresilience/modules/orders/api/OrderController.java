package com.cloudresilience.modules.orders.api;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.orders.application.CheckoutService;
import com.cloudresilience.modules.orders.application.OrderQueryService;
import com.cloudresilience.modules.orders.application.OrderViews.Order;
import com.cloudresilience.modules.orders.application.OrderViews.Page;
import com.cloudresilience.modules.orders.application.OrderViews.Payment;
import com.cloudresilience.modules.orders.application.OrderWorkflowService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    public record CancellationRequest(@NotBlank @Size(max = 500) String reason) {}

    private final CheckoutService checkout;
    private final OrderQueryService queries;
    private final OrderWorkflowService workflow;

    public OrderController(CheckoutService checkout, OrderQueryService queries, OrderWorkflowService workflow) {
        this.checkout = checkout;
        this.queries = queries;
        this.workflow = workflow;
    }

    @PostMapping("/checkout")
    public Order checkout(@AuthenticationPrincipal UserPrincipal customer,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CheckoutRequest request) {
        return checkout.checkout(customer.id(), idempotencyKey, request);
    }

    @GetMapping("/orders")
    public Page<Order> customerOrders(@AuthenticationPrincipal UserPrincipal customer,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.customerOrders(customer.id(), page, size);
    }

    @GetMapping("/orders/{orderId}")
    public Order customerOrder(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID orderId) {
        return queries.customerOrder(customer.id(), orderId);
    }

    @GetMapping("/orders/{orderId}/payment")
    public Payment payment(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID orderId) {
        return queries.customerPayment(customer.id(), orderId);
    }

    @PostMapping("/orders/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID orderId,
            @Valid @RequestBody CancellationRequest request) {
        workflow.cancelCustomerOrder(customer.id(), orderId, request.reason());
    }
}
