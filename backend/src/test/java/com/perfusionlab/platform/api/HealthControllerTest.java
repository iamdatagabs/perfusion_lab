package com.perfusionlab.platform.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void advertisesCapabilitiesProvidedByTheSpringBootApi() {
        var response = new HealthController().health();

        assertEquals("UP", response.get("status"));
        assertEquals("perfusionlab-api", response.get("service"));
        @SuppressWarnings("unchecked")
        var capabilities = (List<String>) response.get("capabilities");
        assertTrue(capabilities.contains("grid"));
        assertTrue(capabilities.contains("timeline"));
        assertTrue(capabilities.contains("multivariate-timeline"));
        assertTrue(capabilities.contains("ode-pde-coupled"));
    }
}
