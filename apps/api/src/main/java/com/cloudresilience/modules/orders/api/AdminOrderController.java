package com.cloudresilience.modules.orders.api;

import com.cloudresilience.modules.identity.application.UserPrincipal;
import com.cloudresilience.modules.orders.application.OrderQueryService;
import com.cloudresilience.modules.orders.application.OrderViews.AdminSummary;
import com.cloudresilience.modules.orders.application.OrderViews.Order;
import com.cloudresilience.modules.orders.application.OrderViews.Page;
import com.cloudresilience.modules.orders.application.OrderWorkflowService;
import com.cloudresilience.modules.orders.domain.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {
    public record StatusRequest(@NotNull OrderStatus status, @Size(max = 500) String reason) {}

    private final OrderQueryService queries;
    private final OrderWorkflowService workflow;

    public AdminOrderController(OrderQueryService queries, OrderWorkflowService workflow) {
        this.queries = queries;
        this.workflow = workflow;
    }

    @GetMapping
    public Page<Order> orders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Size(max = 254) String q) {
        return queries.adminOrders(page, size, status, q);
    }

    @GetMapping("/summary")
    public AdminSummary summary() { return queries.summary(); }

    @GetMapping("/{orderId}")
    public Order order(@PathVariable UUID orderId) { return queries.adminOrder(orderId); }

    @PatchMapping("/{orderId}/status")
    public Order transition(@AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID orderId,
            @Valid @RequestBody StatusRequest request) {
        workflow.transitionAdmin(admin.id(), orderId, request.status(), request.reason());
        return queries.adminOrder(orderId);
    }
}
