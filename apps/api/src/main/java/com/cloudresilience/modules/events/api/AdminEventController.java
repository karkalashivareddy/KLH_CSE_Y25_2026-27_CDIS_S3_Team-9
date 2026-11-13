package com.cloudresilience.modules.events.api;

import com.cloudresilience.modules.events.application.DeadLetterService;
import com.cloudresilience.modules.events.application.EngagementQueryService;
import com.cloudresilience.modules.events.application.EventViews;
import com.cloudresilience.modules.events.application.EventViews.DeadLetter;
import com.cloudresilience.modules.events.application.EventViews.ReplayResult;
import com.cloudresilience.modules.identity.application.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/events")
public class AdminEventController {
    public record ReplayRequest(@NotBlank @Size(max = 500) String reason) {}

    private final DeadLetterService deadLetters;
    private final EngagementQueryService queries;

    public AdminEventController(DeadLetterService deadLetters, EngagementQueryService queries) {
        this.deadLetters = deadLetters;
        this.queries = queries;
    }

    @GetMapping("/outbox")
    public EventViews.OutboxSummary outbox() { return deadLetters.outboxSummary(); }

    @GetMapping("/dead-letters")
    public List<DeadLetter> deadLetters(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return deadLetters.list(status, page, size);
    }

    @PostMapping("/dead-letters/{deadLetterId}/replay")
    public ReplayResult replay(@AuthenticationPrincipal UserPrincipal admin, @PathVariable UUID deadLetterId,
            @Valid @RequestBody ReplayRequest request) {
        return deadLetters.replay(deadLetterId, admin.id(), request.reason());
    }

    @GetMapping("/orders/{orderId}/activity")
    public List<EngagementQueryService.Activity> activity(@PathVariable UUID orderId) {
        return queries.orderActivity(orderId);
    }
}
