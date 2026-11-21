package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.WorkerNodeStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class WorkerNodeStore {
    private final JdbcTemplate jdbc;

    public WorkerNodeStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void heartbeat(String workerId, ExecutionProfile profile, String hostname,
                          int cpuCores, int memoryMb, int slots, Instant startedAt) {
        jdbc.update("""
                INSERT INTO worker_nodes
                    (worker_id, execution_profile, hostname, cpu_cores, memory_mb, slots,
                     started_at, heartbeat_at, software_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, now(), '0.1.0')
                ON CONFLICT (worker_id) DO UPDATE SET
                    execution_profile = EXCLUDED.execution_profile,
                    hostname = EXCLUDED.hostname,
                    cpu_cores = EXCLUDED.cpu_cores,
                    memory_mb = EXCLUDED.memory_mb,
                    slots = EXCLUDED.slots,
                    heartbeat_at = now()
                """, workerId, profile.name(), hostname, cpuCores, memoryMb, slots,
                Timestamp.from(startedAt));
    }

    public List<WorkerNodeStatus> activeNodes() {
        return jdbc.query("""
                SELECT worker_id, execution_profile, hostname, cpu_cores, memory_mb, slots,
                       started_at, heartbeat_at, software_version
                FROM worker_nodes
                WHERE heartbeat_at >= now() - interval '2 minutes'
                ORDER BY execution_profile, hostname
                """, this::map);
    }

    private WorkerNodeStatus map(ResultSet rs, int rowNum) throws SQLException {
        return new WorkerNodeStatus(
                rs.getString("worker_id"),
                ExecutionProfile.valueOf(rs.getString("execution_profile")),
                rs.getString("hostname"), rs.getInt("cpu_cores"),
                rs.getInt("memory_mb"), rs.getInt("slots"),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("heartbeat_at").toInstant(),
                rs.getString("software_version"));
    }
}