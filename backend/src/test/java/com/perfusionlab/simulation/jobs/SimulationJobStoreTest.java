package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SimulationJobStoreTest {
    @Test
    void createsQueuedJobInPersistentStore() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimulationJobStore store = new SimulationJobStore(jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();
        var input = new SimulationPreviewRequest(4.8, 10.0, 0.98, 1200.0, 1.9, 35.5);

        var queued = store.create(id, input);

        assertEquals(id, queued.jobId());
        assertEquals(SimulationJobStatus.State.QUEUED, queued.state());
        assertNotNull(queued.submittedAt());
        verify(jdbc).update(contains("INSERT INTO simulation_jobs"), eq(id),
                any(java.sql.Timestamp.class), anyString());
    }

    @Test
    void onlyOneWorkerCanClaimAQueuedJob() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("UPDATE simulation_jobs"), anyString(), anyLong(), any(UUID.class)))
                .thenReturn(1, 0);
        SimulationJobStore store = new SimulationJobStore(jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();

        assertTrue(store.markRunning(id, "worker-a", java.time.Duration.ofMinutes(2)));
        assertFalse(store.markRunning(id, "worker-b", java.time.Duration.ofMinutes(2)));
    }

    @Test
    void completionIsFencedByWorkerIdentity() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SimulationJobStore store = new SimulationJobStore(jdbc, new ObjectMapper().findAndRegisterModules());
        UUID id = UUID.randomUUID();
        var result = new com.perfusionlab.simulation.api.SimulationPreviewResponse("test-model", java.time.Instant.now(), 72.0, 630.34, 331.76, 4.8, 35.5, "test");

        store.markSucceeded(id, "worker-current", result);

        verify(jdbc).update(contains("AND worker_id = ?"), any(java.sql.Timestamp.class),
                anyString(), eq(id), eq("worker-current"));
    }
}