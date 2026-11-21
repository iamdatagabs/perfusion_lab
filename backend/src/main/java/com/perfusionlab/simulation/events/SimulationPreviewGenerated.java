package com.perfusionlab.simulation.events;

import java.time.Instant;

public record SimulationPreviewGenerated(
    String modelVersion,
    Instant occurredAt,
    double pumpFlowLMin,
    double estimatedOxygenDeliveryMlMin
) {}