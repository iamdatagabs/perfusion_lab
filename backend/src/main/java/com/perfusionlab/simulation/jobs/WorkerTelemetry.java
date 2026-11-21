package com.perfusionlab.simulation.jobs;

/** Best-effort runtime measurements; CPU load is system-level and heap values are JVM-level. */
public record WorkerTelemetry(
        Double systemCpuLoad,
        long jvmHeapUsedMb,
        long jvmHeapMaxMb
) {}
