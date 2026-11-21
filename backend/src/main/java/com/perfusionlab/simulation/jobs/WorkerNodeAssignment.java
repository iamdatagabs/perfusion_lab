package com.perfusionlab.simulation.jobs;

import java.util.UUID;

public record WorkerNodeAssignment(String nodeId, UUID incarnationId) {
}
