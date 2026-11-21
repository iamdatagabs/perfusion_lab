package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExecutionProfileTest {
    @Test
    void profilesHaveDedicatedRoutingAndQueueNames() {
        assertEquals("simulation.execute.general", ExecutionProfile.GENERAL.routingKey());
        assertEquals("perfusionlab.simulation.jobs", ExecutionProfile.GENERAL.queueName());
        assertEquals("simulation.execute.memory", ExecutionProfile.MEMORY.routingKey());
        assertEquals("perfusionlab.simulation.memory", ExecutionProfile.MEMORY.queueName());
        assertEquals("simulation.execute.compute", ExecutionProfile.COMPUTE.routingKey());
        assertEquals("perfusionlab.simulation.compute", ExecutionProfile.COMPUTE.queueName());
    }

    @Test
    void legacyMessagesDefaultToGeneralProfile() {
        var input = new SimulationPreviewRequest(4.8, 10.0, 0.98, 1200.0, 1.9, 35.5);
        var message = new SimulationJobMessage(UUID.randomUUID(), input);
        assertEquals(ExecutionProfile.GENERAL, message.executionProfile());
    }

    @Test
    void nullProfileDefaultsToGeneralForPersistedLegacyEvents() {
        var input = new SimulationPreviewRequest(4.8, 10.0, 0.98, 1200.0, 1.9, 35.5);
        var message = new SimulationJobMessage(UUID.randomUUID(), input, null);
        assertEquals(ExecutionProfile.GENERAL, message.executionProfile());
    }
}
