package com.perfusionlab.simulation.pde;

import com.perfusionlab.simulation.api.CoupledFieldRequest;
import com.perfusionlab.simulation.api.CoupledFieldResponse;
import com.perfusionlab.simulation.api.OdeSimulationResponse;
import com.perfusionlab.simulation.domain.CoupledOdePdePerfusionModel;
import com.perfusionlab.simulation.api.CoupledSimulationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Explicit transient 2-D axial/transverse convection-diffusion-reaction model.
 * Educational numerical prototype; not a validated tissue oxygenation model.
 */
@Component
public class CoupledConcentrationFieldModel {
    private static final double SAFETY = 0.72;
    private static final long MAX_STEPS = 1_000_000L;
    private static final long MAX_NODE_UPDATES = 40_000_000L;
    private final CoupledOdePdePerfusionModel coupledModel;

    public CoupledConcentrationFieldModel(CoupledOdePdePerfusionModel coupledModel) {
        this.coupledModel = coupledModel;
    }

    public CoupledFieldResponse evaluate(CoupledFieldRequest request) {
        if (request == null || request.coupled() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coupled ODE/PDE parameters are required.");
        }
        CoupledSimulationResponse coupled = coupledModel.evaluate(request.coupled());
        int nx = request.coupled().spatialNodes();
        int nz = request.transverseNodes();
        double lengthX = request.coupled().domainLengthMm();
        double lengthZ = request.transverseDomainMm();
        double dx = lengthX / (nx - 1.0);
        double dz = lengthZ / (nz - 1.0);
        double maxVelocity = coupled.maximumAxialVelocityMmPerSecond();
        double diffusion = request.coupled().dispersionMm2PerSecond();
        double reaction = request.coupled().consumptionRatePerSecond();
        double rate = maxVelocity / dx + 2.0 * diffusion / (dx * dx)
            + 2.0 * diffusion / (dz * dz) + reaction;
        double stableDt = rate > 0.0 ? SAFETY / rate : request.coupled().ode().durationSeconds();
        long steps = Math.max(1L, (long) Math.ceil(request.coupled().ode().durationSeconds() / stableDt));
        if (steps > MAX_STEPS || steps * (long) nx * nz > MAX_NODE_UPDATES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "The 2-D field exceeds the explicit solver budget. Reduce duration, grid resolution, velocity or dispersion.");
        }
        double dt = request.coupled().ode().durationSeconds() / steps;
        double[][] state = new double[nx][nz];
        double[][] stage = new double[nx][nz];
        double[][] second = new double[nx][nz];
        double[][] next = new double[nx][nz];
        for (int i = 0; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                state[i][j] = request.coupled().initialConcentration();
            }
        }
        applyInlet(state, request.coupled().inletConcentration());

        List<CoupledFieldResponse.Snapshot> snapshots = new ArrayList<>();
        snapshots.add(snapshot(0.0, state, dx, dz));
        int captureEvery = Math.max(1, (int) Math.ceil((double) steps / (request.snapshots() - 1)));
        for (long step = 0; step < steps; step++) {
            double time = step * dt;
            double u0 = velocityAt(coupled.odeTrajectory(), time, request.coupled().effectiveCrossSectionAreaMm2());
            double u1 = velocityAt(coupled.odeTrajectory(), Math.min(coupled.simulatedDurationSeconds(), time + dt),
                request.coupled().effectiveCrossSectionAreaMm2());
            advance(state, stage, request, dx, dz, dt, u0);
            advance(stage, second, request, dx, dz, dt, u1);
            for (int i = 0; i < nx; i++) {
                for (int j = 0; j < nz; j++) next[i][j] = 0.5 * state[i][j] + 0.5 * second[i][j];
            }
            double[][] swap = state; state = next; next = swap;
            if ((step + 1) % captureEvery == 0 || step == steps - 1) {
                snapshots.add(snapshot((step + 1) * dt, state, dx, dz));
            }
        }
        return new CoupledFieldResponse("0.1.0-coupled-2d-field", Instant.now(), nx, nz, dx, dz,
            coupled.simulatedDurationSeconds(), maxVelocity, List.copyOf(snapshots),
            "SSP-RK2 with first-order upwind axial advection and second-order central diffusion in X and Z; first-order relaxation reaction. Axial inlet: Dirichlet; axial outlet and transverse boundaries: zero-gradient Neumann.",
            "Two-dimensional axial-transverse mathematical prototype. The transverse domain is an idealized slab, not patient anatomy or measured tissue geometry. Concentration is dimensionless; reaction is phenomenological. Uniform transverse inlet and homogeneous coefficients are assumed. No capillary network, anisotropic tissue, oxygen binding/dissociation, validated metabolic uptake or patient-specific calibration. Educational/research use only; not clinically validated.");
    }

    private void advance(double[][] current, double[][] result, CoupledFieldRequest r,
        double dx, double dz, double dt, double velocity) {
        int nx = current.length, nz = current[0].length;
        double diffusion = r.coupled().dispersionMm2PerSecond();
        double reaction = r.coupled().consumptionRatePerSecond();
        for (int j = 0; j < nz; j++) result[0][j] = r.coupled().inletConcentration();
        for (int i = 1; i < nx; i++) {
            for (int j = 0; j < nz; j++) {
                double center = current[i][j];
                double adv = velocity >= 0 ? (center - current[i - 1][j]) / dx : (current[Math.min(nx - 1, i + 1)][j] - center) / dx;
                double lapX = i == nx - 1
                    ? 2.0 * (current[i - 1][j] - center) / (dx * dx)
                    : (current[i + 1][j] - 2.0 * center + current[i - 1][j]) / (dx * dx);
                int jm = Math.max(0, j - 1), jp = Math.min(nz - 1, j + 1);
                double lapZ = j == 0
                    ? 2.0 * (current[i][1] - center) / (dz * dz)
                    : j == nz - 1
                        ? 2.0 * (current[i][nz - 2] - center) / (dz * dz)
                        : (current[i][jp] - 2.0 * center + current[i][jm]) / (dz * dz);
                double derivative = -velocity * adv + diffusion * (lapX + lapZ)
                    - reaction * (center - r.coupled().equilibriumConcentration());
                result[i][j] = center + dt * derivative;
            }
        }
    }

    private void applyInlet(double[][] field, double concentration) {
        for (int j = 0; j < field[0].length; j++) field[0][j] = concentration;
    }

    private CoupledFieldResponse.Snapshot snapshot(double time, double[][] field, double dx, double dz) {
        List<CoupledFieldResponse.Point> points = new ArrayList<>(field.length * field[0].length);
        for (int i = 0; i < field.length; i++) {
            for (int j = 0; j < field[i].length; j++) {
                double value = field[i][j];
                if (!Double.isFinite(value) || value < -1e-7 || value > 1.0 + 1e-7) {
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "2-D numerical solution left normalized concentration range [0,1]. Refine the grid or review parameters.");
                }
                points.add(new CoupledFieldResponse.Point(round(i * dx), round(j * dz), round(Math.max(0, Math.min(1, value)))));
            }
        }
        return new CoupledFieldResponse.Snapshot(round(time), List.copyOf(points));
    }

    private double velocityAt(List<OdeSimulationResponse.Point> points, double time, double area) {
        if (time <= points.get(0).timeSeconds()) return CoupledOdePdePerfusionModel.flowToVelocity(points.get(0).flowLMin(), area);
        int low = 0, high = points.size() - 1;
        while (low + 1 < high) {
            int mid = (low + high) >>> 1;
            if (points.get(mid).timeSeconds() <= time) low = mid; else high = mid;
        }
        var a = points.get(low); var b = points.get(high);
        if (time >= b.timeSeconds()) return CoupledOdePdePerfusionModel.flowToVelocity(b.flowLMin(), area);
        double f = (time - a.timeSeconds()) / (b.timeSeconds() - a.timeSeconds());
        return CoupledOdePdePerfusionModel.flowToVelocity(a.flowLMin() + f * (b.flowLMin() - a.flowLMin()), area);
    }

    private double round(double value) { return Math.round(value * 1_000_000_000_000.0) / 1_000_000_000_000.0; }
}
