package com.perfusionlab.simulation.jobs;

public enum ExecutionProfile {
    GENERAL, MEMORY, COMPUTE;

    public String routingKey() {
        return "simulation.execute." + name().toLowerCase();
    }

    public String queueName() {
        return "perfusionlab.simulation." + name().toLowerCase();
    }
}
