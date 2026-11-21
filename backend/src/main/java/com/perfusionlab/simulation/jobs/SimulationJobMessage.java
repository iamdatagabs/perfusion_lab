package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;

public record SimulationJobMessage(UUID jobId, SimulationPreviewRequest input) {}
