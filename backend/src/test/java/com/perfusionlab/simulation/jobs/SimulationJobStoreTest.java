package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SimulationJobStoreTest {
    @Test
    void createsQueuedJobAndMovesItToRunning() {
        var store = new SimulationJobStore();
        UUID id = UUID.randomUUID();

        var queued = store.create(id);
        assertEquals(SimulationJobStatus.State.QUEUED, queued.state());

        store.markRunning(id);
        var running = store.find(id).orElseThrow();
        assertEquals(SimulationJobStatus.State.RUNNING, running.state());
        assertNotNull(running.startedAt());
        assertEquals(queued.submittedAt(), running.submittedAt());
    }
}
