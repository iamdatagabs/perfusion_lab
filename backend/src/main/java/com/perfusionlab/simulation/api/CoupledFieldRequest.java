package com.perfusionlab.simulation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Inputs for the transient two-dimensional axial-transverse concentration field. */
public record CoupledFieldRequest(
    @NotNull @Valid CoupledSimulationRequest coupled,
    @NotNull @Min(5) @Max(101) Integer transverseNodes,
    @NotNull @Min(1) @Max(100.0) Double transverseDomainMm,
    @NotNull @Min(2) @Max(101) Integer snapshots
) {}
