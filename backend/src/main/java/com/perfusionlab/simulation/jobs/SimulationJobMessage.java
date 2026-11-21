package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;

public record SimulationJobMessage(
        UUID jobId,
        SimulationPreviewRequest input,
        ExecutionProfile executionProfile,
        String targetNodeId,
        UUID targetIncarnationId,
        String correlationId,
        String traceparent
) {
    public SimulationJobMessage {
        if (executionProfile == null) executionProfile = ExecutionProfile.GENERAL;
        if (correlationId == null || correlationId.isBlank()) correlationId = jobId.toString();
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input, ExecutionProfile profile) {
        this(jobId, input, profile, null, null, jobId.toString(), null);
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input, ExecutionProfile profile,
                                String targetNodeId, UUID targetIncarnationId) {
        this(jobId, input, profile, targetNodeId, targetIncarnationId, jobId.toString(), null);
    }

    public SimulationJobMessage(UUID jobId, SimulationPreviewRequest input) {
        this(jobId, input, ExecutionProfile.GENERAL, null, null, jobId.toString(), null);
    }

    public SimulationJobMessage withContext(String correlationId, String traceparent) {
        return new SimulationJobMessage(jobId, input, executionProfile, targetNodeId,
                targetIncarnationId, correlationId, traceparent);
    }
}
