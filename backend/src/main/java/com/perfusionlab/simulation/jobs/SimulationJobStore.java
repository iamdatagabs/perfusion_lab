package com.perfusionlab.simulation.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.perfusionlab.simulation.api.SimulationPreviewRequest;
import com.perfusionlab.simulation.api.SimulationPreviewResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class SimulationJobStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<SimulationJobStatus> jobRowMapper = this::mapRow;

    public SimulationJobStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public SimulationJobStatus create(UUID id, SimulationPreviewRequest input) {
        return create(id, input, null, ExecutionProfile.GENERAL, null);
    }

    public SimulationJobStatus create(UUID id, SimulationPreviewRequest input, UUID batchId,
                                      ExecutionProfile profile) {
        return create(id, input, batchId, profile, null);
    }

    public SimulationJobStatus create(UUID id, SimulationPreviewRequest input, UUID batchId,
                                      ExecutionProfile profile, WorkerNodeAssignment assignment) {
        Instant submittedAt = Instant.now();
        jdbc.update("""
                INSERT INTO simulation_jobs
                    (job_id, state, submitted_at, input_json, batch_id, execution_profile,
                     target_node_id, target_incarnation_id)
                VALUES (?, 'QUEUED', ?, ?::jsonb, ?, ?, ?, ?)
                """, id, Timestamp.from(submittedAt), toJson(input), batchId, profile.name(),
                assignment == null ? null : assignment.nodeId(),
                assignment == null ? null : assignment.incarnationId());
        return new SimulationJobStatus(id, SimulationJobStatus.State.QUEUED,
                submittedAt, null, null, null, null);
    }

    public Optional<SimulationJobStatus> find(UUID id) {
        return jdbc.query("SELECT * FROM simulation_jobs WHERE job_id = ?",
                jobRowMapper, id).stream().findFirst();
    }

    public boolean markRunning(UUID id, String workerId, Duration lease) {
        return jdbc.update("""
                WITH eligible_node AS MATERIALIZED (
                    SELECT worker_id, node_id, incarnation_id
                    FROM worker_nodes
                    WHERE worker_id = ? AND node_status = 'READY'
                    FOR UPDATE
                )
                UPDATE simulation_jobs j
                SET state = 'RUNNING', started_at = now(), worker_id = ?,
                    heartbeat_at = now(), lease_expires_at = now() + (? * interval '1 second'),
                    attempt_count = attempt_count + 1
                FROM eligible_node n
                WHERE j.job_id = ? AND j.state = 'QUEUED'
                  AND (j.target_node_id IS NULL OR
                       (j.target_node_id = n.node_id AND
                        j.target_incarnation_id = n.incarnation_id))
                """, workerId, workerId, lease.toSeconds(), id) == 1;
    }

    public void heartbeat(String workerId, Duration lease) {
        jdbc.update("""
                UPDATE simulation_jobs
                SET heartbeat_at = now(),
                    lease_expires_at = now() + (? * interval '1 second')
                WHERE worker_id = ? AND state = 'RUNNING'
                """, lease.toSeconds(), workerId);
    }

    public void markSucceeded(UUID id, String workerId, SimulationPreviewResponse result) {
        jdbc.update("""
                UPDATE simulation_jobs
                SET state = 'SUCCEEDED', completed_at = ?, result_json = ?::jsonb, error = NULL,
                    worker_id = NULL, heartbeat_at = NULL, lease_expires_at = NULL
                WHERE job_id = ? AND state = 'RUNNING' AND worker_id = ?
                """, Timestamp.from(Instant.now()), toJson(result), id, workerId);
    }

    public void markFailed(UUID id, String workerId, Exception error) {
        jdbc.update("""
                UPDATE simulation_jobs
                SET state = 'FAILED', completed_at = ?, error = ?,
                    worker_id = NULL, heartbeat_at = NULL, lease_expires_at = NULL
                WHERE job_id = ? AND state = 'RUNNING' AND worker_id = ?
                """, Timestamp.from(Instant.now()), error.getClass().getSimpleName(), id, workerId);
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize simulation job data", exception);
        }
    }

    private SimulationJobStatus mapRow(ResultSet rs, int rowNum) throws SQLException {
        SimulationJobStatus.State state = SimulationJobStatus.State.valueOf(rs.getString("state"));
        String resultJson = rs.getString("result_json");
        SimulationPreviewResponse result = null;
        if (resultJson != null) {
            try {
                result = mapper.readValue(resultJson, SimulationPreviewResponse.class);
            } catch (JsonProcessingException exception) {
                throw new SQLException("Could not deserialize simulation result", exception);
            }
        }
        return new SimulationJobStatus(
                rs.getObject("job_id", UUID.class), state,
                instant(rs.getTimestamp("submitted_at")),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("completed_at")), result, rs.getString("error"));
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
