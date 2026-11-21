package com.perfusionlab.simulation.api;

import java.time.Instant;

public record SimulationPreviewResponse(
    String modelVersion,
    Instant generatedAt,
    double estimatedMapMmHg,
    double estimatedOxygenDeliveryMlMin,
    double estimatedOxygenDeliveryIndexMlMinM2,
    double pumpFlowLMin,
    double temperatureC,
    String interpretation
) {}