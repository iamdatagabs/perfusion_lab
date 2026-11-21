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

        verify(jdbc).update(org.mockito.ArgumentMatchers.<String>argThat(sql -> sql.contains("correlation_id")
                && sql.contains("'correlationId', correlation_id")
                && sql.contains("FOR UPDATE SKIP LOCKED")));
    }
}
