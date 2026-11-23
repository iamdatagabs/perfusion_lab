package com.perfusionlab.odepreview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.perfusionlab.simulation.api.PdeTransportRequest;
import com.perfusionlab.simulation.api.PdeTransportResponse;
import com.perfusionlab.simulation.pde.OxygenTransportPdeModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
        assertEquals(0.4, result.minimumConcentration(), 1e-9);
        assertEquals(0.4, result.maximumConcentration(), 1e-9);
        assertEquals(41, result.profile().size());
    }

    @Test
    void explicitSolutionRespectsConcentrationBoundsAndBoundaryConditions() {
        PdeTransportResponse result = model.evaluate(request(81));
        assertEquals(0.95, result.inletConcentration(), 1e-9);
        assertTrue(result.minimumConcentration() >= 0.0);
        assertTrue(result.maximumConcentration() <= 1.0);
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

    @Test
    void manufacturedDiffusionModeShowsSecondOrderGridConvergence() {
        double length = 10.0;
        double diffusivity = 0.8;
        double duration = 5.0;
        PdeTransportResponse coarse = solveManufacturedMode(41, length, diffusivity, duration);
        PdeTransportResponse medium = solveManufacturedMode(81, length, diffusivity, duration);
        PdeTransportResponse fine = solveManufacturedMode(161, length, diffusivity, duration);

        double eCoarse = analyticalError(coarse, length, diffusivity, duration);
        double eMedium = analyticalError(medium, length, diffusivity, duration);
        double eFine = analyticalError(fine, length, diffusivity, duration);
        double order1 = Math.log(eCoarse / eMedium) / Math.log(2.0);
        double order2 = Math.log(eMedium / eFine) / Math.log(2.0);

        assertTrue(eMedium < eCoarse, "Error did not decrease on medium grid.");
        assertTrue(eFine < eMedium, "Error did not decrease on fine grid.");
        assertTrue(order1 > 1.7 && order1 < 2.3, "Unexpected coarse-to-medium order: " + order1);
        assertTrue(order2 > 1.7 && order2 < 2.3, "Unexpected medium-to-fine order: " + order2);
    }

    private PdeTransportResponse solveManufacturedMode(int nodes, double length, double diffusivity, double duration) {
        List<Double> initial = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) {
            double x = length * i / (nodes - 1.0);
            initial.add(Math.sin(Math.PI * x / (2.0 * length)));
        }
        return model.evaluate(new PdeTransportRequest(length, 0.0, diffusivity, 0.0,
            0.0, 0.0, 0.0, duration, nodes, initial));
    }

    private double analyticalError(PdeTransportResponse result, double length, double diffusivity, double time) {
        double lambda = Math.PI / (2.0 * length);
        double sum = 0.0;
        for (PdeTransportResponse.Point point : result.profile()) {
            double exact = Math.sin(lambda * point.xMm())
                * Math.exp(-diffusivity * lambda * lambda * time);
            double delta = point.concentration() - exact;
            sum += delta * delta;
        }
        return Math.sqrt(sum / result.profile().size());
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

    @Test
    void rejectsInitialProfileWithWrongLength() {
        var invalid = new PdeTransportRequest(10.0, 0.0, 0.0, 0.0,
            0.4, 0.4, 0.4, 5.0, 41, List.of(0.4, 0.4, 0.4));
        var error = assertThrows(ResponseStatusException.class, () -> model.evaluate(invalid));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsInitialProfileOutsideNormalizedRange() {
        List<Double> profile = new ArrayList<>(41);
        for (int i = 0; i < 41; i++) profile.add(0.4);
        profile.set(20, 1.01);
        var invalid = new PdeTransportRequest(10.0, 0.0, 0.0, 0.0,
            0.4, 0.4, 0.4, 5.0, 41, profile);
        var error = assertThrows(ResponseStatusException.class, () -> model.evaluate(invalid));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsScenariosThatExceedTheExplicitStepBudget() {
        var expensive = new PdeTransportRequest(1.0, 20.0, 10.0, 1.0,
            0.2, 0.2, 0.9, 1200.0, 500);
        var error = assertThrows(ResponseStatusException.class, () -> model.evaluate(expensive));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

}
