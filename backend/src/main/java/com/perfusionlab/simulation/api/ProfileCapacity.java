package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;

public record ProfileCapacity(
        ExecutionProfile executionProfile,
        int activeNodes,
        int totalSlots,
        int availableSlots,
        int totalCpuCores,
        long totalMemoryMb,
        Double averageSystemCpuLoad,
        Double averageJvmHeapPressure
) {}
