package com.perfusionlab.simulation.pde;

import com.perfusionlab.simulation.api.PdeTransportRequest;
import com.perfusionlab.simulation.api.PdeTransportResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * One-dimensional educational convection-diffusion-reaction model.
 * Concentration is dimensionless and normalized to [0,1].
 * This is a numerical sandbox, not a validated tissue oxygenation model.
 */
@Component
public class OxygenTransportPdeModel {
    public static final String MODEL_VERSION = "0.3.0-pde-transport";
    private static final double SAFETY_FACTOR = 0.80;
    private static final long MAX_STEPS = 2_000_000L;

    public PdeTransportResponse evaluate(PdeTransportRequest p) {
        validate(p);
        int n = p.spatialNodes();
        double dx = p.domainLengthMm() / (n - 1);
        double u = p.velocityMmPerSecond();
        double diffusion = p.dispersionMm2PerSecond();
        double reaction = p.consumptionRatePerSecond();

        // Forward-Euler monotonicity bound; SSP-RK2 inherits this strong-stability limit.
        double rate = u / dx + 2.0 * diffusion / (dx * dx) + reaction;
        double dt = rate > 0.0 ? SAFETY_FACTOR / rate : p.durationSeconds();
        long steps = (long) Math.ceil(p.durationSeconds() / dt);
        if (steps > MAX_STEPS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Requested grid and parameters require too many explicit time steps; reduce nodes, duration, velocity or dispersion.");
        }
        dt = p.durationSeconds() / Math.max(1L, steps);

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

        // SSP-RK2 (Heun): second-order time integration as a convex combination
        // of two forward-Euler stages, preserving the forward-Euler monotonicity bound.
        for (long step = 0; step < steps; step++) {
            forwardEuler(state, stage, p, dx, dt, u, diffusion, reaction);
            forwardEuler(stage, secondStage, p, dx, dt, u, diffusion, reaction);
            for (int i = 0; i < n; i++) {
                next[i] = 0.5 * state[i] + 0.5 * secondStage[i];
            }
            double[] swap = state;
            state = next;
            next = swap;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        List<PdeTransportResponse.Point> profile = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(state[i]) || state[i] < -1e-8 || state[i] > 1.0 + 1e-8) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Numerical solution left the normalized concentration range; refine the grid or review parameters.");
            }
            min = Math.min(min, state[i]);
            max = Math.max(max, state[i]);
            profile.add(new PdeTransportResponse.Point(round(i * dx), round(state[i])));
        }

        return new PdeTransportResponse(
            MODEL_VERSION, "SSP_RK2_UPWIND_CENTRAL_DIFFUSION", Instant.now(),
            p.domainLengthMm(), n, dx, dt, p.durationSeconds(),
            p.inletConcentration(), round(state[n - 1]), round(min), round(max),
            List.copyOf(profile),
            "Inlet: prescribed concentration (Dirichlet). Outlet: zero spatial gradient (Neumann), implemented with a mirrored ghost node. Initial field: uniform or user-supplied nodal profile.",
            "Dimensionless one-dimensional convection-diffusion-reaction prototype. Time integration uses SSP-RK2; advection is first-order upwind and diffusion is second-order central. Consumption is a first-order relaxation term, not measured metabolic uptake. No capillary geometry, hemoglobin dissociation, membrane exchange, heterogeneous tissue, or patient-specific physiology. Educational/research use only; not clinically validated."
        );
    }

    private void forwardEuler(
        double[] current, double[] result, PdeTransportRequest p,
        double dx, double dt, double u, double diffusion, double reaction
    ) {
        int n = current.length;
        result[0] = p.inletConcentration();
        for (int i = 1; i < n - 1; i++) {
            double gradient = u >= 0.0
                ? (current[i] - current[i - 1]) / dx
                : (current[i + 1] - current[i]) / dx;
            double laplacian = (current[i + 1] - 2.0 * current[i] + current[i - 1]) / (dx * dx);
            double derivative = -u * gradient + diffusion * laplacian
                - reaction * (current[i] - p.equilibriumConcentration());
            result[i] = current[i] + dt * derivative;
        }

        // Neumann outlet: mirrored ghost node gives dC/dx = 0 at x=L.
        double outletLaplacian = diffusion > 0.0
            ? 2.0 * (current[n - 2] - current[n - 1]) / (dx * dx) : 0.0;
        double outletDerivative = diffusion * outletLaplacian
            - reaction * (current[n - 1] - p.equilibriumConcentration());
        result[n - 1] = current[n - 1] + dt * outletDerivative;
    }

    private void validate(PdeTransportRequest p) {
        if (p == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request is required.");
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

    private double round(double x) { return Math.round(x * 1_000_000_000_000.0) / 1_000_000_000_000.0; }
}
