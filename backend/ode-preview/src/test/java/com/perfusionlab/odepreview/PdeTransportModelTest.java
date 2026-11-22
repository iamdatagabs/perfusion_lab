package com.perfusionlab.odepreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.perfusionlab.simulation.api.PdeTransportRequest;
import com.perfusionlab.simulation.api.PdeTransportResponse;
import com.perfusionlab.simulation.pde.OxygenTransportPdeModel;
import org.junit.jupiter.api.Test;

class PdeTransportModelTest {
    private final OxygenTransportPdeModel model = new OxygenTransportPdeModel();

    private PdeTransportRequest request(int nodes) {
        return new PdeTransportRequest(20.0, 0.8, 0.15, 0.025,
            0.2, 0.25, 0.95, 12.0, nodes);
    }

    @Test
    void constantEquilibriumRemainsConstant() {
        PdeTransportRequest p = new PdeTransportRequest(10.0, 0.0, 0.0, 0.0,
            0.4, 0.4, 0.4, 5.0, 41);
        PdeTransportResponse result = model.evaluate(p);
        assertEquals(0.4, result.minimumConcentration(), 1e-6);
        assertEquals(0.4, result.maximumConcentration(), 1e-6);
        assertEquals(41, result.profile().size());
    }

    @Test
    void explicitSolutionRespectsConcentrationBoundsAndBoundaryConditions() {
        PdeTransportResponse result = model.evaluate(request(81));
        assertEquals(0.95, result.inletConcentration(), 1e-9);
        assertTrue(result.minimumConcentration() >= 0.0);
        assertTrue(result.maximumConcentration() <= 1.0);
        assertEquals(result.profile().get(79).concentration(),
            result.profile().get(80).concentration(), 1e-6);
        assertTrue(result.stableTimeStepSeconds() > 0.0);
    }

    @Test
    void spatialGridRefinementConvergesTowardFineGridReference() {
        PdeTransportResponse coarse = model.evaluate(request(41));
        PdeTransportResponse medium = model.evaluate(request(81));
        PdeTransportResponse fine = model.evaluate(request(161));
        PdeTransportResponse reference = model.evaluate(request(321));
        double coarseError = profileError(coarse, reference);
        double mediumError = profileError(medium, reference);
        double fineError = profileError(fine, reference);
        assertTrue(mediumError < coarseError,
            "Medium-grid error should be lower than coarse-grid error: " + mediumError + " vs " + coarseError);
        assertTrue(fineError < mediumError,
            "Fine-grid error should be lower than medium-grid error: " + fineError + " vs " + mediumError);
    }

    private double profileError(PdeTransportResponse candidate, PdeTransportResponse reference) {
        double sum = 0.0;
        for (PdeTransportResponse.Point point : candidate.profile()) {
            int index = (int) Math.round(point.xMm() / reference.dxMm());
            double delta = point.concentration() - reference.profile().get(index).concentration();
            sum += delta * delta;
        }
        return Math.sqrt(sum / candidate.profile().size());
    }
}
