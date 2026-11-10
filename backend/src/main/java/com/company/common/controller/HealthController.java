package com.company.common.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    public record Liveness(boolean success, Map<String, Object> meta) {}

    @GetMapping(value = "/api/health", produces = "application/json")
    public Liveness getHealth() {
        return new Liveness(true, Map.of());
    }
}
