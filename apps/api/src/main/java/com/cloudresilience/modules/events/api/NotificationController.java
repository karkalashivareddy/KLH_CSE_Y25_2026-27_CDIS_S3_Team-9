package com.cloudresilience.modules.events.api;

import com.cloudresilience.modules.events.application.EngagementQueryService;
import com.cloudresilience.modules.events.application.EventViews;
import com.cloudresilience.modules.events.application.EventViews.Notification;
import com.cloudresilience.modules.identity.application.UserPrincipal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class NotificationController {
    private final EngagementQueryService queries;

    public NotificationController(EngagementQueryService queries) { this.queries = queries; }

    @GetMapping("/notifications")
    public EventViews.Page<Notification> notifications(@AuthenticationPrincipal UserPrincipal customer,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return queries.notifications(customer.id(), page, size);
    }

    @PostMapping("/notifications/{notificationId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal UserPrincipal customer, @PathVariable UUID notificationId) {
        queries.markRead(customer.id(), notificationId);
    }
}
