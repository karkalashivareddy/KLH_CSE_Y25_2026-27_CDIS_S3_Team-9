package com.cloudresilience.modules.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthController {
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "application", "cloudresilience-api");
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of("application", "cloudresilience-api", "version", "0.1.0", "phase", "foundation");
    }
}
