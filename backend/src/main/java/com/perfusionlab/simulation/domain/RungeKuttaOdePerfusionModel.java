package com.perfusionlab.simulation.domain;

import com.perfusionlab.simulation.api.OdeSimulationRequest;
import com.perfusionlab.simulation.api.OdeSimulationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Low-dimensional educational ODE model integrated with fixed-step RK4.
 * It models a first-order flow response and a lumped resistance-compliance pressure compartment.
 * It is not clinically validated and is not intended for patient care.
 */
@Component
public class RungeKuttaOdePerfusionModel {
    public static final String MODEL_VERSION = "0.2.0-ode-prototype";
    private static final double SVR_DYN_TO_MMHG_S_PER_ML = 1333.22;
    private static final int MAX_POINTS = 3601;

    public OdeSimulationResponse evaluate(OdeSimulationRequest input) {
        validate(input);
        double dt = input.stepSeconds();
        int steps = (int) Math.ceil(input.durationSeconds() / dt);
        if (steps + 1 > MAX_POINTS) {
            throw new IllegalArgumentException("Simulation exceeds 3601 points; increase stepSeconds.");
        }

        // State vector: [flow (L/min), arterial-equivalent pressure (mmHg)].
        double[] state = {input.initialFlowLMin(), initialPressure(input)};
        List<OdeSimulationResponse.Point> points = new ArrayList<>(steps + 1);
        double time = 0.0;
        points.add(point(time, state, input));

        for (int i = 0; i < steps; i++) {
            double h = Math.min(dt, input.durationSeconds() - time);
            state = rk4(time, state, h, input);
            time += h;
            if (Math.abs(time - input.durationSeconds()) < 1e-9) time = input.durationSeconds();
            points.add(point(time, state, input));
        }

        return new OdeSimulationResponse(
            MODEL_VERSION,
            "RK4_FIXED_STEP",
            Instant.now(),
            dt,
            List.copyOf(points),
            "Two-state lumped model: first-order pump-flow response and resistance-compliance pressure compartment.",
            "Educational mathematical prototype only. Parameters are not patient-specific; no clinical validation, treatment recommendation or use in patient care. Oxygen delivery is a derived estimate, not a dynamic tissue oxygenation model."
        );
    }

    private double[] derivative(double[] y, OdeSimulationRequest p) {
        double flow = y[0];
        double pressure = y[1];
        double resistance = p.systemicVascularResistance() / SVR_DYN_TO_MMHG_S_PER_ML;
        double flowMlPerSecond = flow * 1000.0 / 60.0;
        double outflowMlPerSecond = (pressure - p.venousPressureMmHg()) / resistance;
        double dFlow = (p.targetFlowLMin() - flow) / p.flowTimeConstantSeconds();
        double dPressure = (flowMlPerSecond - outflowMlPerSecond) / p.arterialComplianceMlPerMmHg();
        return new double[]{dFlow, dPressure};
    }

    private double[] rk4(double t, double[] y, double h, OdeSimulationRequest p) {
        double[] k1 = derivative(y, p);
        double[] k2 = derivative(add(y, k1, h / 2.0), p);
        double[] k3 = derivative(add(y, k2, h / 2.0), p);
        double[] k4 = derivative(add(y, k3, h), p);
        return new double[]{
            y[0] + h * (k1[0] + 2*k2[0] + 2*k3[0] + k4[0]) / 6.0,
            y[1] + h * (k1[1] + 2*k2[1] + 2*k3[1] + k4[1]) / 6.0
        };
    }

    private double[] add(double[] y, double[] k, double factor) {
        return new double[]{y[0] + factor*k[0], y[1] + factor*k[1]};
    }

    private OdeSimulationResponse.Point point(double time, double[] state, OdeSimulationRequest p) {
        double oxygenContent = 1.34 * p.hemoglobinGdL() * p.arterialSaturation();
        double do2 = state[0] * 10.0 * oxygenContent;
        return new OdeSimulationResponse.Point(
            round(time), round(state[0]), round(state[1]), round(do2), round(do2 / p.bodySurfaceAreaM2())
        );
    }

    private double initialPressure(OdeSimulationRequest p) {
        double resistance = p.systemicVascularResistance() / SVR_DYN_TO_MMHG_S_PER_ML;
        return p.venousPressureMmHg() + (p.initialFlowLMin() * 1000.0 / 60.0) * resistance;
    }

    private void validate(OdeSimulationRequest p) {
        if (p == null) throw new IllegalArgumentException("Request is required.");
        if (p.durationSeconds() / p.stepSeconds() > MAX_POINTS - 1) {
            throw new IllegalArgumentException("Simulation exceeds 3601 points; increase stepSeconds.");
        }
    }

    private double round(double value) { return Math.round(value * 10000.0) / 10000.0; }
}
