package com.perfusionlab.simulation.pde;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.perfusionlab.simulation.api.CoupledFieldRequest;
import com.perfusionlab.simulation.api.CoupledSimulationRequest;
import com.perfusionlab.simulation.api.OdeSimulationRequest;
import com.perfusionlab.simulation.domain.CoupledOdePdePerfusionModel;
import org.junit.jupiter.api.Test;

class CoupledConcentrationFieldModelTest {
    private final CoupledConcentrationFieldModel model =
        new CoupledConcentrationFieldModel(new CoupledOdePdePerfusionModel(
            new com.perfusionlab.simulation.domain.RungeKuttaOdePerfusionModel()));

    @Test
    void producesTransientAxialTransverseSnapshots() {
        var ode = new OdeSimulationRequest(2.0, 4.0, 10.0, .98, 1200.0,
            1.8, 2.0, 3.0, 5.0, 2.0, .1);
        var coupled = new CoupledSimulationRequest(ode, 10_000.0, 20.0,
            .15, .025, .25, .2, .98, 21, null);
        var result = model.evaluate(new CoupledFieldRequest(coupled, 9, 8.0, 5));
        assertEquals(21, result.axialNodes());
        assertEquals(9, result.transverseNodes());
        assertTrue(result.snapshots().size() >= 2);
        var first = result.snapshots().get(0);
        var last = result.snapshots().get(result.snapshots().size() - 1);
        assertEquals(21 * 9, last.points().size());
        assertEquals(.98, first.points().get(0).concentration(), 1e-9);
        assertTrue(last.points().stream().allMatch(p -> p.concentration() >= 0 && p.concentration() <= 1));
        assertTrue(last.timeSeconds() > 0);
    }
}
