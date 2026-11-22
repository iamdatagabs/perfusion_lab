package com.perfusionlab.simulation.api;

import java.time.Instant;
import java.util.List;

public record OdeSimulationResponse(
    String modelVersion,
    String integrator,
    Instant generatedAt,
    double stepSeconds,
    List<Point> points,
    String modelDescription,
    String limitations
) {
    public record Point(
        double timeSeconds,
        double flowLMin,
        double pressureMmHg,
        double oxygenDeliveryMlMin,
        double oxygenDeliveryIndexMlMinM2
    ) {}
}
