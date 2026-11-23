package com.perfusionlab.simulation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.perfusionlab.simulation.api.CoupledSimulationRequest;
import com.perfusionlab.simulation.api.CoupledSimulationResponse;
import com.perfusionlab.simulation.api.OdeSimulationRequest;
import org.junit.jupiter.api.Test;

class CoupledOdePdePerfusionModelTest {
    private final CoupledOdePdePerfusionModel model =
        new CoupledOdePdePerfusionModel(new RungeKuttaOdePerfusionModel());

    private OdeSimulationRequest ode(double initial, double target) {
        return new OdeSimulationRequest(initial, target, 10.0, 0.98,
            1200.0, 1.8, 2.0, 3.0, 5.0, 10.0, 0.1);
    }

    private CoupledSimulationRequest request(OdeSimulationRequest ode) {
        return new CoupledSimulationRequest(ode, 10_000.0, 20.0,
            0.15, 0.0, 0.2, 0.2, 0.95, 41, null);
    }

    @Test
    void convertsLitresPerMinuteToMillimetresPerSecondUsingExplicitArea() {
        // 6 L/min = 100,000 mm^3/s; divided by 10,000 mm^2 = 10 mm/s.
        assertEquals(10.0, CoupledOdePdePerfusionModel.flowToVelocity(6.0, 10_000.0), 1e-12);
    }

    @Test
    void couplesOdeFlowTrajectoryToPdeVelocityTrajectory() {
        CoupledSimulationResponse result = model.evaluate(request(ode(2.0, 6.0)));
        assertEquals(result.odeTrajectory().size(), result.velocityTrajectory().size());
        assertTrue(result.velocityTrajectory().get(0).axialVelocityMmPerSecond()
            < result.velocityTrajectory().get(result.velocityTrajectory().size() - 1).axialVelocityMmPerSecond());
        assertTrue(result.maximumAxialVelocityMmPerSecond() > 0.0);
        assertEquals(41, result.finalConcentrationProfile().size());
        assertTrue(result.minimumConcentration() >= 0.0);
        assertTrue(result.maximumConcentration() <= 1.0);
        assertEquals(0.95, result.finalConcentrationProfile().get(0).concentration(), 1e-9);
    }

    @Test
    void constantFlowProducesConstantVelocityTrajectory() {
        CoupledSimulationResponse result = model.evaluate(request(ode(4.0, 4.0)));
        double first = result.velocityTrajectory().get(0).axialVelocityMmPerSecond();
        for (var point : result.velocityTrajectory()) {
            assertEquals(first, point.axialVelocityMmPerSecond(), 1e-9);
        }
    }
}
