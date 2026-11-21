package com.perfusionlab.simulation.jobs;

import java.lang.management.ManagementFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkerTelemetryCollector {
    private final com.sun.management.OperatingSystemMXBean operatingSystem;

    public WorkerTelemetryCollector() {
        var bean = ManagementFactory.getOperatingSystemMXBean();
        this.operatingSystem = bean instanceof com.sun.management.OperatingSystemMXBean extended
                ? extended : null;
    }

    public WorkerTelemetry collect() {
        Double cpuLoad = null;
        if (operatingSystem != null) {
            double value = operatingSystem.getCpuLoad();
            if (Double.isFinite(value) && value >= 0.0) {
                cpuLoad = Math.min(1.0, value);
            }
        }
        Runtime runtime = Runtime.getRuntime();
        long usedBytes = runtime.totalMemory() - runtime.freeMemory();
        long maxBytes = runtime.maxMemory();
        return new WorkerTelemetry(cpuLoad, toMb(usedBytes), toMb(maxBytes));
    }

    private long toMb(long bytes) {
        return Math.max(0L, bytes / (1024L * 1024L));
    }
}
