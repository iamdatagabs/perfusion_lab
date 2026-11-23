package com.perfusionlab.simulation.api;

import java.time.Instant;
import java.util.List;

/** Joint output: lumped ODE trajectory and distributed PDE concentration field. */
public record CoupledSimulationResponse(
    String modelVersion,
    Instant generatedAt,
    double effectiveCrossSectionAreaMm2,
    double domainLengthMm,
    int spatialNodes,
    double dxMm,
    double pdeTimeStepSeconds,
    double simulatedDurationSeconds,
    double maximumAxialVelocityMmPerSecond,
    double meanAxialVelocityMmPerSecond,
    List<OdeSimulationResponse.Point> odeTrajectory,
    List<VelocityPoint> velocityTrajectory,
    List<PdeTransportResponse.Point> finalConcentrationProfile,
    double outletConcentration,
    double minimumConcentration,
    double maximumConcentration,
    String couplingDescription,
    String limitations
) {
    public record VelocityPoint(double timeSeconds, double flowLMin, double axialVelocityMmPerSecond) {}
}
