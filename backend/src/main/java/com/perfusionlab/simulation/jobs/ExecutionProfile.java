package com.perfusionlab.simulation.jobs;

public enum ExecutionProfile {
    GENERAL, MEMORY, COMPUTE;

    public String routingKey() {
        return "simulation.execute." + name().toLowerCase();
    }

    public String queueName() {
        return this == GENERAL ? "perfusionlab.simulation.jobs"
                : "perfusionlab.simulation." + name().toLowerCase();
    }
}
