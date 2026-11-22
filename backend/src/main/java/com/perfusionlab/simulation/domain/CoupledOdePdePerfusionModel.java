package com.perfusionlab.simulation.domain;

import com.perfusionlab.simulation.api.CoupledSimulationRequest;
import com.perfusionlab.simulation.api.CoupledSimulationResponse;
import com.perfusionlab.simulation.api.OdeSimulationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Experimental one-way coupling: ODE pump flow drives the PDE axial velocity.
 * The supplied cross-sectional area is an abstract effective model parameter,
 * not an anatomical or cannula measurement.
 */
@Component
public class CoupledOdePdePerfusionModel {
    public static final String MODEL_VERSION = "0.1.0-ode-pde-coupled";
    private static final double SAFETY_FACTOR = 0.80;
    private static final long MAX_PDE_STEPS = 2_000_000L;
    private final RungeKuttaOdePerfusionModel odeModel;

    public CoupledOdePdePerfusionModel(RungeKuttaOdePerfusionModel odeModel) {
        this.odeModel = odeModel;
    }

    public CoupledSimulationResponse evaluate(CoupledSimulationRequest p) {
        validate(p);
        OdeSimulationResponse ode = odeModel.evaluate(p.ode());
        int n = p.spatialNodes();
        double dx = p.domainLengthMm() / (n - 1.0);
        double area = p.effectiveCrossSectionAreaMm2();

        List<CoupledSimulationResponse.VelocityPoint> velocitySeries = new ArrayList<>();
        double maxVelocity = 0.0;
        double velocityIntegral = 0.0;
        for (int i = 0; i < ode.points().size(); i++) {
            OdeSimulationResponse.Point point = ode.points().get(i);
            double velocity = flowToVelocity(point.flowLMin(), area);
            maxVelocity = Math.max(maxVelocity, velocity);
            velocitySeries.add(new CoupledSimulationResponse.VelocityPoint(
                point.timeSeconds(), point.flowLMin(), round(velocity)));
            if (i > 0) {
                OdeSimulationResponse.Point previous = ode.points().get(i - 1);
                double previousVelocity = flowToVelocity(previous.flowLMin(), area);
                velocityIntegral += 0.5 * (previousVelocity + velocity)
                    * (point.timeSeconds() - previous.timeSeconds());
            }
        }

        double duration = p.ode().durationSeconds();
        double rate = maxVelocity / dx
            + 2.0 * p.dispersionMm2PerSecond() / (dx * dx)
            + p.consumptionRatePerSecond();
        double stableLimit = rate > 0.0 ? SAFETY_FACTOR / rate : duration;
        long steps = Math.max(1L, (long) Math.ceil(duration / stableLimit));
        if (steps > MAX_PDE_STEPS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Coupled PDE requires too many explicit time steps. Increase effectiveCrossSectionAreaMm2, reduce dispersion or spatial resolution, or shorten the experiment.");
        }
        double dt = duration / steps;

        double[] state = new double[n];
        double[] stage = new double[n];
        double[] secondStage = new double[n];
        double[] next = new double[n];
        if (p.initialProfile() == null) {
            for (int i = 0; i < n; i++) state[i] = p.initialConcentration();
        } else {
            for (int i = 0; i < n; i++) state[i] = p.initialProfile().get(i);
        }
        state[0] = p.inletConcentration();

        for (long step = 0; step < steps; step++) {
            double t = step * dt;
            double u0 = interpolateFlow(ode.points(), t, area);
            double u1 = interpolateFlow(ode.points(), Math.min(duration, t + dt), area);
            forwardEuler(state, stage, p, dx, dt, u0);
            forwardEuler(stage, secondStage, p, dx, dt, u1);
            for (int i = 0; i < n; i++) next[i] = 0.5 * state[i] + 0.5 * secondStage[i];
            double[] swap = state;
            state = next;
            next = swap;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        List<com.perfusionlab.simulation.api.PdeTransportResponse.Point> profile = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(state[i]) || state[i] < -1e-8 || state[i] > 1.0 + 1e-8) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Coupled numerical solution left normalized concentration range [0,1]. Refine the grid or review parameters.");
            }
            min = Math.min(min, state[i]);
            max = Math.max(max, state[i]);
            profile.add(new com.perfusionlab.simulation.api.PdeTransportResponse.Point(
                round(i * dx), round(state[i])));
        }

        return new CoupledSimulationResponse(
            MODEL_VERSION, Instant.now(), area, p.domainLengthMm(), n, dx, dt, duration,
            round(maxVelocity), round(velocityIntegral / duration),
            ode.points(), List.copyOf(velocitySeries), List.copyOf(profile),
            round(state[n - 1]), round(min), round(max),
            "One-way coupling: ODE pump flow Q(t) is converted to axial velocity u(t)=Q(t)/A using the explicitly supplied effectiveCrossSectionAreaMm2. The velocity is linearly interpolated in time and drives the SSP-RK2 PDE transport solver.",
            "Research/educational mathematical prototype only. Effective area is an abstract computational parameter, not a patient, vessel, cannula or oxygenator measurement. The PDE concentration is dimensionless; the reaction term is phenomenological, not measured metabolic uptake. No feedback from PDE to ODE, no validated physiology, and no clinical use."
        );
    }

    private void forwardEuler(double[] current, double[] result,
        CoupledSimulationRequest p, double dx, double dt, double velocity) {
        int n = current.length;
        result[0] = p.inletConcentration();
        for (int i = 1; i < n - 1; i++) {
            double gradient = velocity >= 0.0
                ? (current[i] - current[i - 1]) / dx
                : (current[i + 1] - current[i]) / dx;
            double laplacian = (current[i + 1] - 2.0 * current[i] + current[i - 1]) / (dx * dx);
            double derivative = -velocity * gradient
                + p.dispersionMm2PerSecond() * laplacian
                - p.consumptionRatePerSecond() * (current[i] - p.equilibriumConcentration());
            result[i] = current[i] + dt * derivative;
        }
        double outletLaplacian = p.dispersionMm2PerSecond() > 0.0
            ? 2.0 * (current[n - 2] - current[n - 1]) / (dx * dx) : 0.0;
        double outletDerivative = p.dispersionMm2PerSecond() * outletLaplacian
            - p.consumptionRatePerSecond() * (current[n - 1] - p.equilibriumConcentration());
        result[n - 1] = current[n - 1] + dt * outletDerivative;
    }

    private double interpolateFlow(List<OdeSimulationResponse.Point> points, double time, double area) {
        if (time <= points.get(0).timeSeconds()) return flowToVelocity(points.get(0).flowLMin(), area);
        for (int i = 1; i < points.size(); i++) {
            OdeSimulationResponse.Point right = points.get(i);
            if (time <= right.timeSeconds()) {
                OdeSimulationResponse.Point left = points.get(i - 1);
                double fraction = (time - left.timeSeconds()) / (right.timeSeconds() - left.timeSeconds());
                double flow = left.flowLMin() + fraction * (right.flowLMin() - left.flowLMin());
                return flowToVelocity(flow, area);
            }
        }
        return flowToVelocity(points.get(points.size() - 1).flowLMin(), area);
    }

    /** 1 L = 1,000,000 mm^3; convert L/min to mm^3/s and divide by mm^2. */
    static double flowToVelocity(double flowLMin, double areaMm2) {
        return flowLMin * 1_000_000.0 / 60.0 / areaMm2;
    }

    private void validate(CoupledSimulationRequest p) {
        if (p == null || p.ode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ODE parameters are required.");
        }
        if (p.initialProfile() != null) {
            if (p.initialProfile().size() != p.spatialNodes()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "initialProfile must contain exactly spatialNodes values.");
            }
            for (Double value : p.initialProfile()) {
                if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "initialProfile values must be finite and normalized to [0,1].");
                }
            }
        }
    }

    private double round(double value) {
        return Math.round(value * 1_000_000_000_000.0) / 1_000_000_000_000.0;
    }
}
