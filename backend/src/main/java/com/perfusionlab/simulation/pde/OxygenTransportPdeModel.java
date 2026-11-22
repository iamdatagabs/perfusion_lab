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
    public static final String MODEL_VERSION = "0.2.0-pde-transport";
    private static final double SAFETY_FACTOR = 0.80;
    private static final long MAX_STEPS = 2_000_000L;

    public PdeTransportResponse evaluate(PdeTransportRequest p) {
        validate(p);
        int n = p.spatialNodes();
        double dx = p.domainLengthMm() / (n - 1);
        double u = p.velocityMmPerSecond();
        double diffusion = p.dispersionMm2PerSecond();
        double reaction = p.consumptionRatePerSecond();

        // Sufficient monotonicity bound for forward Euler + upwind advection + central diffusion.
        double rate = u / dx + 2.0 * diffusion / (dx * dx) + reaction;
        double dt = rate > 0.0 ? SAFETY_FACTOR / rate : p.durationSeconds();
        long steps = (long) Math.ceil(p.durationSeconds() / dt);
        if (steps > MAX_STEPS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Requested grid and parameters require too many explicit time steps; reduce nodes, duration, velocity or dispersion.");
        }
        dt = p.durationSeconds() / Math.max(1L, steps);

        double[] c = new double[n];
        double[] next = new double[n];
        if (p.initialProfile() == null) {
            for (int i = 0; i < n; i++) c[i] = p.initialConcentration();
        } else {
            for (int i = 0; i < n; i++) c[i] = p.initialProfile().get(i);
        }
        c[0] = p.inletConcentration();

        for (long step = 0; step < steps; step++) {
            next[0] = p.inletConcentration(); // Dirichlet inlet.
            for (int i = 1; i < n - 1; i++) {
                double gradient = u >= 0.0
                    ? (c[i] - c[i - 1]) / dx
                    : (c[i + 1] - c[i]) / dx;
                double laplacian = (c[i + 1] - 2.0 * c[i] + c[i - 1]) / (dx * dx);
                double dc = -u * gradient + diffusion * laplacian
                    - reaction * (c[i] - p.equilibriumConcentration());
                next[i] = c[i] + dt * dc;
            }

            // Neumann outlet via a mirrored ghost node: dC/dx = 0 at x=L.
            double outletLaplacian = diffusion > 0.0
                ? 2.0 * (c[n - 2] - c[n - 1]) / (dx * dx) : 0.0;
            double outletRate = outletLaplacian
                - reaction * (c[n - 1] - p.equilibriumConcentration());
            next[n - 1] = c[n - 1] + dt * outletRate;

            double[] swap = c;
            c = next;
            next = swap;
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        List<PdeTransportResponse.Point> profile = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(c[i]) || c[i] < -1e-8 || c[i] > 1.0 + 1e-8) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Numerical solution left the normalized concentration range; refine the grid or review parameters.");
            }
            min = Math.min(min, c[i]);
            max = Math.max(max, c[i]);
            profile.add(new PdeTransportResponse.Point(round(i * dx), round(c[i])));
        }

        return new PdeTransportResponse(
            MODEL_VERSION, "FORWARD_EULER_UPWIND_CENTRAL_DIFFUSION", Instant.now(),
            p.domainLengthMm(), n, dx, dt, p.durationSeconds(),
            p.inletConcentration(), round(c[n - 1]), round(min), round(max),
            List.copyOf(profile),
            "Inlet: prescribed concentration (Dirichlet). Outlet: zero spatial gradient (Neumann), implemented with a mirrored ghost node. Initial field: uniform or user-supplied nodal profile.",
            "Dimensionless one-dimensional convection-diffusion-reaction prototype. Consumption is represented by a first-order relaxation term; no capillary geometry, hemoglobin dissociation, membrane exchange, heterogeneous tissue, or patient-specific physiology. Educational/research use only; not clinically validated."
        );
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
