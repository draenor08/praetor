package com.praetor.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Skeleton liveness endpoint. Proves the app booted and the /api base path is
 * reachable through the frontend nginx proxy. Real modules replace nothing here;
 * they add their own controllers under com.praetor.&lt;module&gt;.controller.
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "praetor-backend");
    }
}
