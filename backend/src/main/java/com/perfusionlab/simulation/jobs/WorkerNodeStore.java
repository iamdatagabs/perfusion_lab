package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.ProfileCapacity;
import com.perfusionlab.simulation.api.WorkerNodeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkerNodeStore {
    private final JdbcTemplate jdbc;
    public WorkerNodeStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /**
     * Locks one eligible node until the caller's transaction commits. The
     * caller inserts the assigned job in that same transaction; the job row
     * itself is the durable slot reservation.
     */
    public Optional<WorkerNodeAssignment> reserveNode(ExecutionProfile profile) {
        try {
            return Optional.of(jdbc.queryForObject("""
                    SELECT n.node_id, n.incarnation_id
                    FROM worker_nodes n
                    WHERE n.execution_profile = ?
                      AND n.node_status = 'READY'
                      AND n.heartbeat_at >= now() - interval '2 minutes'
                      AND n.slots > (
                          SELECT COUNT(*)
                          FROM simulation_jobs j
                          WHERE (j.state = 'RUNNING' AND j.worker_id = n.worker_id)
                             OR (j.state = 'QUEUED' AND j.target_node_id = n.node_id
                                 AND j.target_incarnation_id = n.incarnation_id)
                      )
                    ORDER BY (
                        SELECT COUNT(*)
                        FROM simulation_jobs j
                        WHERE (j.state = 'RUNNING' AND j.worker_id = n.worker_id)
                           OR (j.state = 'QUEUED' AND j.target_node_id = n.node_id
                               AND j.target_incarnation_id = n.incarnation_id)
                    ) ASC,
                    COALESCE(n.system_cpu_load, 0.0) ASC,
                    n.heartbeat_at DESC
                    FOR UPDATE OF n SKIP LOCKED
                    LIMIT 1
                    """, (rs, rowNum) -> new WorkerNodeAssignment(
                    rs.getString("node_id"), rs.getObject("incarnation_id", UUID.class)),
                    profile.name()));
        } catch (EmptyResultDataAccessException noCapacity) {
            return Optional.empty();
        }
    }

    public void heartbeat(String workerId, String nodeId, UUID incarnationId,
                          ExecutionProfile profile, String hostname, int cpuCores,
                          int memoryMb, int slots, Instant startedAt, WorkerTelemetry telemetry) {
        jdbc.update("""
                INSERT INTO worker_nodes
                    (worker_id, node_id, incarnation_id, node_status, execution_profile,
                     hostname, cpu_cores, memory_mb, slots, started_at, heartbeat_at,
                     software_version, system_cpu_load, jvm_heap_used_mb, jvm_heap_max_mb)
                VALUES (?, ?, ?, 'READY', ?, ?, ?, ?, ?, ?, now(), '0.1.0', ?, ?, ?)
                ON CONFLICT (worker_id) DO UPDATE SET
                    node_id = EXCLUDED.node_id, incarnation_id = EXCLUDED.incarnation_id,
                    node_status = CASE WHEN worker_nodes.node_status IN ('DRAINING', 'OFFLINE') THEN worker_nodes.node_status ELSE 'READY' END, execution_profile = EXCLUDED.execution_profile,
                    hostname = EXCLUDED.hostname, cpu_cores = EXCLUDED.cpu_cores,
                    memory_mb = EXCLUDED.memory_mb, slots = EXCLUDED.slots,
                    heartbeat_at = now(), system_cpu_load = EXCLUDED.system_cpu_load,
                    jvm_heap_used_mb = EXCLUDED.jvm_heap_used_mb,
                    jvm_heap_max_mb = EXCLUDED.jvm_heap_max_mb
                """, workerId, nodeId, incarnationId, profile.name(), hostname,
                cpuCores, memoryMb, slots, Timestamp.from(startedAt),
                telemetry.systemCpuLoad(), telemetry.jvmHeapUsedMb(), telemetry.jvmHeapMaxMb());
    }

    public List<WorkerNodeStatus> activeNodes() {
        return jdbc.query("""
                SELECT n.worker_id, n.node_id, n.incarnation_id, n.node_status,
                       n.execution_profile, n.hostname, n.cpu_cores, n.memory_mb,
                       n.slots, n.started_at, n.heartbeat_at, n.software_version,
                       n.system_cpu_load, n.jvm_heap_used_mb, n.jvm_heap_max_mb,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'RUNNING'
                           AND j.worker_id = n.worker_id) AS active_jobs,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'QUEUED'
                           AND j.target_node_id = n.node_id
                           AND j.target_incarnation_id = n.incarnation_id) AS reserved_jobs
                FROM worker_nodes n
                LEFT JOIN simulation_jobs j
                  ON (j.worker_id = n.worker_id OR
                      (j.target_node_id = n.node_id AND j.target_incarnation_id = n.incarnation_id))
                WHERE n.heartbeat_at >= now() - interval '2 minutes'
                  AND n.node_status = 'READY'
                GROUP BY n.worker_id
                ORDER BY n.execution_profile, n.hostname
                """, this::map);
    }

    public List<ProfileCapacity> profileCapacities() {
        return jdbc.query("""
                WITH node_load AS (
                    SELECT n.worker_id, n.execution_profile, n.slots, n.cpu_cores, n.memory_mb,
                           n.system_cpu_load, n.jvm_heap_used_mb, n.jvm_heap_max_mb,
                           COUNT(j.job_id) FILTER (
                               WHERE (j.state = 'RUNNING' AND j.worker_id = n.worker_id)
                                  OR (j.state = 'QUEUED' AND j.target_node_id = n.node_id
                                      AND j.target_incarnation_id = n.incarnation_id)
                           ) AS assigned_jobs
                    FROM worker_nodes n
                    LEFT JOIN simulation_jobs j
                      ON (j.worker_id = n.worker_id OR
                          (j.target_node_id = n.node_id AND j.target_incarnation_id = n.incarnation_id))
                    WHERE n.heartbeat_at >= now() - interval '2 minutes'
                      AND n.node_status = 'READY'
                    GROUP BY n.worker_id
                )
                SELECT execution_profile, COUNT(*) AS active_nodes, SUM(slots) AS total_slots,
                       SUM(GREATEST(slots - assigned_jobs, 0)) AS available_slots,
                       SUM(cpu_cores) AS total_cpu_cores, SUM(memory_mb) AS total_memory_mb,
                       AVG(system_cpu_load) AS average_system_cpu_load,
                       AVG(CASE WHEN jvm_heap_max_mb > 0
                           THEN jvm_heap_used_mb::double precision / jvm_heap_max_mb END)
                           AS average_jvm_heap_pressure
                FROM node_load GROUP BY execution_profile ORDER BY execution_profile
                """, (rs, rowNum) -> new ProfileCapacity(
                ExecutionProfile.valueOf(rs.getString("execution_profile")),
                rs.getInt("active_nodes"), rs.getInt("total_slots"), rs.getInt("available_slots"),
                rs.getInt("total_cpu_cores"), rs.getLong("total_memory_mb"),
                nullableDouble(rs, "average_system_cpu_load"),
                nullableDouble(rs, "average_jvm_heap_pressure")));
    }

    public String nodeStatus(String nodeId, UUID incarnationId) {
        return jdbc.query("SELECT node_status FROM worker_nodes WHERE node_id = ? AND incarnation_id = ?",
                rs -> rs.next() ? rs.getString(1) : null, nodeId, incarnationId);
    }

    public int markDraining(String nodeId, UUID incarnationId) {
        return jdbc.update("""
                UPDATE worker_nodes SET node_status = 'DRAINING'
                WHERE node_id = ? AND incarnation_id = ? AND node_status = 'READY'
                """, nodeId, incarnationId);
    }

    /** Move waiting work back to the profile queues in the same transaction as drain. */
    public int requeueWaitingAssignments(String nodeId, UUID incarnationId) {
        return jdbc.update("""
                WITH moved AS (
                    UPDATE simulation_jobs
                    SET target_node_id = NULL, target_incarnation_id = NULL
                    WHERE state = 'QUEUED' AND target_node_id = ? AND target_incarnation_id = ?
                    RETURNING job_id, input_json, execution_profile
                )
                INSERT INTO outbox_events
                    (event_id, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
                SELECT gen_random_uuid(), job_id, 'SimulationJobRequested',
                       jsonb_build_object('jobId', job_id, 'input', input_json,
                           'executionProfile', execution_profile,
                           'targetNodeId', NULL, 'targetIncarnationId', NULL),
                       'PENDING', now(), now()
                FROM moved
                """, nodeId, incarnationId);
    }

    public int markOfflineIfIdle(String nodeId, UUID incarnationId) {
        return jdbc.update("""
                UPDATE worker_nodes n SET node_status = 'OFFLINE'
                WHERE n.node_id = ? AND n.incarnation_id = ? AND n.node_status = 'DRAINING'
                  AND NOT EXISTS (SELECT 1 FROM simulation_jobs j
                                  WHERE j.worker_id = n.worker_id AND j.state = 'RUNNING')
                """, nodeId, incarnationId);
    }

    public int completeIdleDrains() {
        return jdbc.update("""
                UPDATE worker_nodes n SET node_status = 'OFFLINE'
                WHERE n.node_status = 'DRAINING'
                  AND NOT EXISTS (SELECT 1 FROM simulation_jobs j
                                  WHERE j.worker_id = n.worker_id AND j.state = 'RUNNING')
                """);
    }

    public WorkerNodeDrainSnapshot drainSnapshot(String nodeId, UUID incarnationId, int reassigned) {
        return jdbc.queryForObject("""
                SELECT n.node_id, n.incarnation_id, n.node_status,
                       (SELECT COUNT(*) FROM simulation_jobs j
                        WHERE j.worker_id = n.worker_id AND j.state = 'RUNNING') AS running_jobs,
                       (SELECT COUNT(*) FROM simulation_jobs j
                        WHERE j.target_node_id = n.node_id AND j.target_incarnation_id = n.incarnation_id
                          AND j.state = 'QUEUED') AS queued_assignments
                FROM worker_nodes n WHERE n.node_id = ? AND n.incarnation_id = ?
                """, (rs, rowNum) -> new WorkerNodeDrainSnapshot(
                rs.getString("node_id"), rs.getObject("incarnation_id", UUID.class),
                rs.getString("node_status"), reassigned, rs.getInt("running_jobs"),
                rs.getInt("queued_assignments")), nodeId, incarnationId);
    }

    private WorkerNodeStatus map(ResultSet rs, int rowNum) throws SQLException {
        int slots = rs.getInt("slots"), active = rs.getInt("active_jobs");
        int reserved = rs.getInt("reserved_jobs");
        return new WorkerNodeStatus(rs.getString("worker_id"), rs.getString("node_id"),
                rs.getObject("incarnation_id", UUID.class), rs.getString("node_status"),
                ExecutionProfile.valueOf(rs.getString("execution_profile")),
                rs.getString("hostname"), rs.getInt("cpu_cores"), rs.getInt("memory_mb"),
                slots, active, Math.max(0, slots - active - reserved),
                rs.getTimestamp("started_at").toInstant(), rs.getTimestamp("heartbeat_at").toInstant(),
                rs.getString("software_version"), nullableDouble(rs, "system_cpu_load"),
                rs.getLong("jvm_heap_used_mb"), rs.getLong("jvm_heap_max_mb"));
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).doubleValue();
    }
}
