package com.perfusionlab.simulation.jobs;

import java.util.UUID;

public record WorkerNodeDrainSnapshot(
        String nodeId,
        UUID incarnationId,
        String status,
        int reassignedQueuedJobs,
        int runningJobs,
        int remainingQueuedAssignments
) {
    public boolean drained() { return "OFFLINE".equals(status); }
}
