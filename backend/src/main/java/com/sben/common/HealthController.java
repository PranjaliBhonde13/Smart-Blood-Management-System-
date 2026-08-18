package com.sben.common;

import com.sben.common.response.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Lightweight liveness endpoint used to confirm the API is up during Module 0.
 * Superseded operationally by Spring Actuator's /actuator/health.
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/ping")
    public ApiResponse<Map<String, String>> ping() {
        return ApiResponse.ok("Service is up", Map.of("service", "sben", "status", "UP"));
    }
}
