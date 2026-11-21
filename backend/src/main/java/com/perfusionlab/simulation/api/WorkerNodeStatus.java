package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;
import java.time.Instant;

public record WorkerNodeStatus(
        String workerId,
        ExecutionProfile executionProfile,
        String hostname,
        int cpuCores,
        int memoryMb,
        int slots,
        int activeJobs,
        int availableSlots,
        Instant startedAt,
        Instant heartbeatAt,
        String softwareVersion
) {}
