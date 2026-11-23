package com.perfusionlab.simulation.api;

import java.time.Instant;
import java.util.List;

/** Time snapshots of a numerically resolved axial-transverse concentration field. */
public record CoupledFieldResponse(
    String modelVersion,
    Instant generatedAt,
    int axialNodes,
    int transverseNodes,
    double dxMm,
    double dzMm,
    double simulatedDurationSeconds,
    double maximumAxialVelocityMmPerSecond,
    List<Snapshot> snapshots,
    String method,
    String limitations
) {
    public record Snapshot(double timeSeconds, List<Point> points) {}
    public record Point(double xMm, double zMm, double concentration) {}
}
