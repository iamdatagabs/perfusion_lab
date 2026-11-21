package com.perfusionlab.simulation.api;

import com.perfusionlab.simulation.jobs.ExecutionProfile;

/** Client-facing profile selector. AUTO is resolved before persistence and routing. */
public enum RequestedExecutionProfile {
    AUTO, GENERAL, MEMORY, COMPUTE;

    public boolean isAutomatic() {
        return this == AUTO;
    }

    public ExecutionProfile toExecutionProfile() {
        if (isAutomatic()) {
            throw new IllegalStateException("AUTO must be resolved by the execution planner");
        }
        return ExecutionProfile.valueOf(name());
    }
}
