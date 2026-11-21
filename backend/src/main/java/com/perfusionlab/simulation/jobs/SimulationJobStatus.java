package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewResponse;
import java.time.Instant;
import java.util.UUID;

public record SimulationJobStatus(
        UUID jobId,
        State state,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        SimulationPreviewResponse result,
        String error
) {
    public enum State { QUEUED, RUNNING, SUCCEEDED, FAILED }
}
