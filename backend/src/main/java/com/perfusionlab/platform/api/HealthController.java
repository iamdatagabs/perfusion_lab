package com.perfusionlab.platform.api;

import java.util.Map;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@CrossOrigin(origins = "*")
public class HealthController {
    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
            "status", "UP",
            "service", "perfusionlab-api",
            "version", "0.1.0",
            "capabilities", List.of(
                "preview",
                "grid",
                "timeline",
                "multivariate-timeline",
                "ode-pde-coupled"
            )
        );
    }
}
