package com.perfusionlab.simulation.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "perfusion.role", havingValue = "api", matchIfMissing = true)
public class SimulationJobRecovery {
    private final JdbcTemplate jdbc;

    public SimulationJobRecovery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Scheduled(fixedDelayString = "${perfusion.worker.recovery-ms:15000}")
    @Transactional
    public void recoverExpiredLeases() {
        // A crashed worker's lease expires; requeue with a fresh outbox event.
        jdbc.update("""
                WITH expired AS (
                    SELECT job_id, input_json, attempt_count
                    FROM simulation_jobs
                    WHERE state = 'RUNNING' AND lease_expires_at < now()
                    FOR UPDATE SKIP LOCKED
                ), exhausted AS (
                    UPDATE simulation_jobs j
                    SET state = 'FAILED', completed_at = now(),
                        error = 'WorkerLeaseExpired: retry limit reached',
                        worker_id = NULL, lease_expires_at = NULL
                    FROM expired e
                    WHERE j.job_id = e.job_id AND e.attempt_count >= 3
                    RETURNING j.job_id
                ), retryable AS (
                    UPDATE simulation_jobs j
                    SET state = 'QUEUED', worker_id = NULL, lease_expires_at = NULL,
                        heartbeat_at = NULL, started_at = NULL,
                        attempt_count = j.attempt_count + 1
                    FROM expired e
                    WHERE j.job_id = e.job_id AND e.attempt_count < 3
                    RETURNING j.job_id, j.input_json
                )
                INSERT INTO outbox_events
                    (event_id, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
                SELECT gen_random_uuid(), job_id, 'SimulationJobRequested',
                       jsonb_build_object('jobId', job_id, 'input', input_json),
                       'PENDING', now(), now()
                FROM retryable
                """);
    }
}