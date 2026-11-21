package com.perfusionlab.simulation.jobs;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import java.sql.Timestamp;
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
                any(Timestamp.class), anyString());
    }

    @Test
    void onlyOneWorkerCanClaimAQueuedJob() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(contains("UPDATE simulation_jobs"), any(Timestamp.class), any(UUID.class)))
                .thenReturn(1, 0);
        SimulationJobStore store = new SimulationJobStore(jdbc, new ObjectMapper());
        UUID id = UUID.randomUUID();

        assertTrue(store.markRunning(id));
        assertFalse(store.markRunning(id));
    }
}
