package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;

public record SimulationJobMessage(
        UUID jobId,
        SimulationPreviewRequest input,
        ExecutionProfile executionProfile,
        String targetNodeId,
        UUID targetIncarnationId
) {
    public SimulationJobMessage {
        if (executionProfile == null) executionProfile = ExecutionProfile.GENERAL;
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input, ExecutionProfile profile) {
        this(jobId, input, profile, null, null);
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input) {
        this(jobId, input, ExecutionProfile.GENERAL, null, null);
    }
}
