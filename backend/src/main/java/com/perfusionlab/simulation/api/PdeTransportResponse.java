package com.perfusionlab.simulation.api;

import java.time.Instant;
import java.util.List;

public record PdeTransportResponse(
    String modelVersion,
    String method,
    Instant generatedAt,
    double domainLengthMm,
    int spatialNodes,
    double dxMm,
    double stableTimeStepSeconds,
    double simulatedDurationSeconds,
    double inletConcentration,
    double outletConcentration,
    double minimumConcentration,
    double maximumConcentration,
    List<Point> profile,
    String boundaryConditions,
    String limitations
) {
    public record Point(double xMm, double concentration) {}
}
