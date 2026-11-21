package com.perfusionlab.simulation.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record SimulationPreviewRequest(
    @NotNull @DecimalMin("0.1") @DecimalMax("10.0") Double pumpFlowLMin,
    @NotNull @DecimalMin("1.0") @DecimalMax("25.0") Double hemoglobinGdL,
    @NotNull @DecimalMin("0.50") @DecimalMax("1.0") Double arterialSaturation,
    @NotNull @DecimalMin("100.0") @DecimalMax("5000.0") Double systemicVascularResistance,
    @NotNull @DecimalMin("0.3") @DecimalMax("3.0") Double bodySurfaceAreaM2,
    @NotNull @DecimalMin("15.0") @DecimalMax("40.0") Double temperatureC
) {}