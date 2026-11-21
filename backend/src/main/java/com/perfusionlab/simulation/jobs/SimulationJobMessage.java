package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;

public record SimulationJobMessage(
        UUID jobId,
        SimulationPreviewRequest input,
        ExecutionProfile executionProfile
) {
    public SimulationJobMessage {
        if (executionProfile == null) executionProfile = ExecutionProfile.GENERAL;
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input) {
        this(jobId, input, ExecutionProfile.GENERAL);
    }
}
