package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;
import java.time.Instant;
import java.util.UUID;

public record ExperimentBatchStatus(
        UUID batchId,
        ExecutionProfile executionProfile,
        RequestedExecutionProfile requestedExecutionProfile,
        String schedulingReason,
        String state,
        int jobCount,
        int queued,
        int running,
        int succeeded,
        int failed,
        int parallelism,
        Instant submittedAt,
        Instant startedAt,
        Instant completedAt,
        Double throughputJobsPerSecond,
        Double speedup,
        Double parallelEfficiency
) {}
