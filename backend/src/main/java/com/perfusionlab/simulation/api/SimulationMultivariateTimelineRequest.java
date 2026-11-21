package com.perfusionlab.simulation.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
public record SimulationMultivariateTimelineRequest(
 @NotNull @Valid SimulationPreviewRequest baseline,
 @NotEmpty @Size(max=6) List<@NotNull @Valid ParameterRamp> ramps,
 @NotNull @Min(1) @Max(3600) Integer durationSeconds,
 @NotNull @Min(1) @Max(300) Integer stepSeconds
) {
 public record ParameterRamp(
  @NotNull @Pattern(regexp="pumpFlowLMin|hemoglobinGdL|arterialSaturation|systemicVascularResistance|bodySurfaceAreaM2|temperatureC") String parameter,
  @NotNull Double endValue
 ) {}
}