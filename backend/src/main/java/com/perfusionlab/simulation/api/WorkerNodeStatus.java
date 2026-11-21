package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;
import java.time.Instant;
import java.util.UUID;

public record WorkerNodeStatus(
        String workerId, String nodeId, UUID incarnationId, String nodeStatus,
        ExecutionProfile executionProfile, String hostname, int cpuCores, int memoryMb,
        int slots, int activeJobs, int availableSlots, Instant startedAt, Instant heartbeatAt,
        String softwareVersion, Double systemCpuLoad, long jvmHeapUsedMb, long jvmHeapMaxMb
) {}
