package com.perfusionlab.simulation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import org.junit.jupiter.api.Test;

class SimplifiedPerfusionModelTest {
    private final SimplifiedPerfusionModel model = new SimplifiedPerfusionModel();

    @Test
    void calculatesEducationalMapAndOxygenDelivery() {
        var input = new SimulationPreviewRequest(4.8, 10.0, 0.98, 1200.0, 1.9, 35.5);
        var result = model.evaluate(input);

        assertEquals(72.0, result.estimatedMapMmHg(), 0.01);
        assertEquals(631.68, result.estimatedOxygenDeliveryMlMin(), 0.01);
        assertEquals(332.46, result.estimatedOxygenDeliveryIndexMlMinM2(), 0.02);
        assertEquals("0.1.0-educational", result.modelVersion());
    }
}