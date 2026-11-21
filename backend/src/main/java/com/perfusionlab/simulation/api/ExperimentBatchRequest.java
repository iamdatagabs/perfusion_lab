package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ExperimentBatchRequest(
        @NotEmpty @Size(max = 1000) List<@Valid SimulationPreviewRequest> simulations,
        @NotNull ExecutionProfile executionProfile,
        @NotNull @Min(1) @Max(256) Integer parallelism,
        @Positive Long baselineSequentialMs
) {}
