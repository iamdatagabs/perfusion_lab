package com.perfusionlab.simulation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Inputs for a mathematical ODE-to-PDE coupling experiment. */
public record CoupledSimulationRequest(
    @NotNull @Valid OdeSimulationRequest ode,
    @NotNull @DecimalMin("100.0") @DecimalMax("1000000.0") Double effectiveCrossSectionAreaMm2,
    @NotNull @DecimalMin("1.0") @DecimalMax("100.0") Double domainLengthMm,
    @NotNull @DecimalMin("0.0") @DecimalMax("10.0") Double dispersionMm2PerSecond,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double consumptionRatePerSecond,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double equilibriumConcentration,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double initialConcentration,
    @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double inletConcentration,
    @NotNull @Min(20) @Max(500) Integer spatialNodes,
    List<Double> initialProfile
) {}
