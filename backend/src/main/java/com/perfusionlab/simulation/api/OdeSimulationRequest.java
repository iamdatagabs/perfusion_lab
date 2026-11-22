package com.perfusionlab.simulation.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OdeSimulationRequest(
    @NotNull @DecimalMin("0.1") @DecimalMax("10.0") Double initialFlowLMin,
    @NotNull @DecimalMin("0.1") @DecimalMax("10.0") Double targetFlowLMin,
    @NotNull @DecimalMin("1.0") @DecimalMax("25.0") Double hemoglobinGdL,
    @NotNull @DecimalMin("0.50") @DecimalMax("1.0") Double arterialSaturation,
    @NotNull @DecimalMin("100.0") @DecimalMax("5000.0") Double systemicVascularResistance,
    @NotNull @DecimalMin("0.3") @DecimalMax("3.0") Double bodySurfaceAreaM2,
    @NotNull @DecimalMin("0.1") @DecimalMax("10.0") Double arterialComplianceMlPerMmHg,
    @NotNull @DecimalMin("0.1") @DecimalMax("60.0") Double flowTimeConstantSeconds,
    @NotNull @DecimalMin("0.0") @DecimalMax("30.0") Double venousPressureMmHg,
    @NotNull @DecimalMin("0.1") @DecimalMax("3600.0") Double durationSeconds,
    @NotNull @DecimalMin("0.01") @DecimalMax("5.0") Double stepSeconds
) {}
