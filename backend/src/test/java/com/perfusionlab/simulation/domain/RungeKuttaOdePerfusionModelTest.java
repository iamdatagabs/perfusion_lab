package com.perfusionlab.simulation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.perfusionlab.simulation.api.OdeSimulationRequest;
import com.perfusionlab.simulation.api.OdeSimulationResponse;
import org.junit.jupiter.api.Test;

class RungeKuttaOdePerfusionModelTest {
    private final RungeKuttaOdePerfusionModel model = new RungeKuttaOdePerfusionModel();

    private OdeSimulationRequest request(double initialFlow, double targetFlow, double step) {
        return new OdeSimulationRequest(initialFlow, targetFlow, 10.0, 0.98, 1200.0, 1.9,
            2.0, 3.0, 5.0, 60.0, step);
    }

    @Test
    void constantFlowRemainsAtItsEquilibrium() {
        OdeSimulationResponse result = model.evaluate(request(4.0, 4.0, 0.1));
        var last = result.points().get(result.points().size() - 1);
        assertEquals(4.0, last.flowLMin(), 1e-8);
        assertEquals(result.points().get(0).pressureMmHg(), last.pressureMmHg(), 1e-8);
    }

    @Test
    void flowApproachesTargetWithoutOvershootingForSmallStep() {
        OdeSimulationResponse result = model.evaluate(request(2.0, 5.0, 0.1));
        var last = result.points().get(result.points().size() - 1);
        assertTrue(last.flowLMin() > 4.9);
        assertTrue(last.flowLMin() < 5.0);
        assertTrue(result.points().stream().allMatch(p -> Double.isFinite(p.pressureMmHg())));
    }

    @Test
    void finalPointMatchesRequestedDuration() {
        OdeSimulationResponse result = model.evaluate(request(4.0, 4.5, 0.7));
        assertEquals(60.0, result.points().get(result.points().size() - 1).timeSeconds(), 1e-8);
    }
}
