package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkerNodeDrainServiceTest {
    @Test
    void drainStopsNewAssignmentsAndRequeuesWaitingJobs() {
        WorkerNodeStore store = mock(WorkerNodeStore.class);
        UUID incarnation = UUID.randomUUID();
        when(store.nodeStatus("node-a", incarnation)).thenReturn("READY");
        when(store.requeueWaitingAssignments("node-a", incarnation)).thenReturn(3);
        when(store.drainSnapshot("node-a", incarnation, 3)).thenReturn(
                new WorkerNodeDrainSnapshot("node-a", incarnation, "DRAINING", 3, 2, 0));

        WorkerNodeDrainSnapshot result = new WorkerNodeDrainService(store).drain("node-a", incarnation);

        verify(store).markDraining("node-a", incarnation);
        verify(store).requeueWaitingAssignments("node-a", incarnation);
        verify(store).markOfflineIfIdle("node-a", incarnation);
        assertEquals(3, result.reassignedQueuedJobs());
        assertEquals(2, result.runningJobs());
        assertFalse(result.drained());
    }

    @Test
    void alreadyOfflineDrainIsIdempotent() {
        WorkerNodeStore store = mock(WorkerNodeStore.class);
        UUID incarnation = UUID.randomUUID();
        when(store.nodeStatus("node-a", incarnation)).thenReturn("OFFLINE");
        when(store.drainSnapshot("node-a", incarnation, 0)).thenReturn(
                new WorkerNodeDrainSnapshot("node-a", incarnation, "OFFLINE", 0, 0, 0));

        WorkerNodeDrainSnapshot result = new WorkerNodeDrainService(store).drain("node-a", incarnation);

        verify(store, never()).markDraining(anyString(), any());
        verify(store, never()).requeueWaitingAssignments(anyString(), any());
        assertTrue(result.drained());
    }
}
