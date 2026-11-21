package com.perfusionlab.simulation.jobs;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SimulationJobRecoveryTest {
    @Test
    void leaseRecoveryKeepsCorrelationAndTraceContext() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new SimulationJobRecovery(jdbc).recoverExpiredLeases();

        verify(jdbc).update(contains("correlation_id"), (Object[]) any());
        verify(jdbc).update(contains("'correlationId', correlation_id"));
        verify(jdbc).update(contains("FOR UPDATE SKIP LOCKED"));
    }
}
