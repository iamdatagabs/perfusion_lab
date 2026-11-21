package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.ProfileCapacity;
import com.perfusionlab.simulation.api.RequestedExecutionProfile;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ExecutionPlanner {
    private final WorkerNodeStore nodes;

    public ExecutionPlanner(WorkerNodeStore nodes) {
        this.nodes = nodes;
    }

    public SchedulingDecision plan(RequestedExecutionProfile requested) {
        if (!requested.isAutomatic()) {
            ExecutionProfile explicit = requested.toExecutionProfile();
            return new SchedulingDecision(explicit, "EXPLICIT_" + explicit.name());
        }

        List<ProfileCapacity> candidates = nodes.profileCapacities().stream()
                .filter(capacity -> capacity.activeNodes() > 0 && capacity.availableSlots() > 0)
                .toList();

        return candidates.stream()
                .max(Comparator.comparingDouble(this::score)
                        .thenComparingInt(ProfileCapacity::availableSlots)
                        .thenComparing(capacity -> capacity.executionProfile().name()))
                .map(capacity -> new SchedulingDecision(
                        capacity.executionProfile(),
                        "AUTO_SELECTED_" + capacity.executionProfile().name()
                                + "_AVAILABLE_SLOTS_" + capacity.availableSlots()))
                .orElseGet(() -> new SchedulingDecision(
                        ExecutionProfile.GENERAL, "AUTO_FALLBACK_GENERAL_NO_LIVE_FREE_SLOTS"));
    }

    private double score(ProfileCapacity capacity) {
        double cpuLoad = clamp(capacity.averageSystemCpuLoad());
        double heapPressure = clamp(capacity.averageJvmHeapPressure());
        return capacity.availableSlots() * 100.0
                + Math.log1p(capacity.totalCpuCores()) * 5.0
                + Math.log1p(capacity.totalMemoryMb() / 1024.0) * 2.0
                - cpuLoad * 40.0
                - heapPressure * 30.0;
    }

    private double clamp(Double value) {
        return value == null || !Double.isFinite(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value));
    }
}
