package com.perfusionlab.simulation.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PdeTransportRequest(
    @NotNull @DecimalMin("1.0") @DecimalMax("100.0") Double domainLengthMm,
    @NotNull @DecimalMin("0.0") @DecimalMax("20.0") Double velocityMmPerSecond,
    @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double dispersionMm2PerSecond,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double consumptionRatePerSecond,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double equilibriumConcentration,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double initialConcentration,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double inletConcentration,
    @NotNull @DecimalMin("0.1") @DecimalMax("1200.0") Double durationSeconds,
    @NotNull @Min(20) @Max(500) Integer spatialNodes,
    List<Double> initialProfile
) {
    /** Backward-compatible constructor: omitted profile means uniform initial concentration. */
    public PdeTransportRequest(
        Double domainLengthMm, Double velocityMmPerSecond, Double dispersionMm2PerSecond,
        Double consumptionRatePerSecond, Double equilibriumConcentration,
        Double initialConcentration, Double inletConcentration,
        Double durationSeconds, Integer spatialNodes
    ) {
        this(domainLengthMm, velocityMmPerSecond, dispersionMm2PerSecond,
            consumptionRatePerSecond, equilibriumConcentration, initialConcentration,
            inletConcentration, durationSeconds, spatialNodes, null);
    }
}
