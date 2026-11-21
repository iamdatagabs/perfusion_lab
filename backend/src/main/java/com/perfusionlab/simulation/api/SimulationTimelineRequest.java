package com.perfusionlab.simulation.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
public record SimulationTimelineRequest(
 @NotNull @Valid SimulationPreviewRequest baseline,
 @NotNull @Pattern(regexp="pumpFlowLMin|hemoglobinGdL|arterialSaturation|systemicVascularResistance|bodySurfaceAreaM2|temperatureC") String parameter,
 @NotNull Double endValue,
 @NotNull @Min(1) @Max(3600) Integer durationSeconds,
 @NotNull @Min(1) @Max(300) Integer stepSeconds
) {}