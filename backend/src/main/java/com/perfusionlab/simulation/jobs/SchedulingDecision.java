package com.perfusionlab.simulation.jobs;

public record SchedulingDecision(ExecutionProfile profile, String reason) {}
