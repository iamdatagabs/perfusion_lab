package com.perfusionlab.simulation.jobs;

import com.perfusionlab.simulation.api.ExperimentBatchStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExperimentBatchStore {
    private final JdbcTemplate jdbc;

    public ExperimentBatchStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, ExecutionProfile profile, int parallelism, int jobCount,
                       Long baselineSequentialMs) {
        jdbc.update("""
                INSERT INTO experiment_batches
                    (batch_id, execution_profile, parallelism, job_count, baseline_sequential_ms)
                VALUES (?, ?, ?, ?, ?)
                """, id, profile.name(), parallelism, jobCount, baselineSequentialMs);
    }

    public Optional<ExperimentBatchStatus> find(UUID id) {
        return jdbc.query("""
                SELECT b.batch_id, b.execution_profile, b.parallelism, b.job_count,
                       b.baseline_sequential_ms, b.submitted_at,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'QUEUED') AS queued,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'RUNNING') AS running,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'SUCCEEDED') AS succeeded,
                       COUNT(j.job_id) FILTER (WHERE j.state = 'FAILED') AS failed,
                       MIN(j.started_at) AS started_at,
                       CASE WHEN COUNT(j.job_id) FILTER (WHERE j.state IN ('QUEUED','RUNNING')) = 0
                            THEN MAX(j.completed_at) END AS completed_at
                FROM experiment_batches b
                LEFT JOIN simulation_jobs j ON j.batch_id = b.batch_id
                WHERE b.batch_id = ?
                GROUP BY b.batch_id
                """, (rs, rowNum) -> mapStatus(rs), id).stream().findFirst();
    }

    private ExperimentBatchStatus mapStatus(ResultSet rs) throws SQLException {
        int queued = rs.getInt("queued");
        int running = rs.getInt("running");
        int succeeded = rs.getInt("succeeded");
        int failed = rs.getInt("failed");
        int total = rs.getInt("job_count");
        Instant submitted = instant(rs.getTimestamp("submitted_at"));
        Instant started = instant(rs.getTimestamp("started_at"));
        Instant completed = instant(rs.getTimestamp("completed_at"));
        String state = queued + running > 0 ? (started == null ? "QUEUED" : "RUNNING")
                : failed > 0 && succeeded == 0 ? "FAILED"
                : failed > 0 ? "PARTIAL" : "SUCCEEDED";
        Double throughput = null;
        Double speedup = null;
        Double efficiency = null;
        if (started != null && (completed != null || succeeded + failed > 0)) {
            Instant end = completed != null ? completed : Instant.now();
            double seconds = Math.max(0.001, DurationMath.secondsBetween(started, end));
            throughput = succeeded / seconds;
            long baseline = rs.getLong("baseline_sequential_ms");
            if (!rs.wasNull() && baseline > 0) {
                speedup = (baseline / 1000.0) / seconds;
                efficiency = speedup / rs.getInt("parallelism");
            }
        }
        return new ExperimentBatchStatus(
                rs.getObject("batch_id", UUID.class),
                ExecutionProfile.valueOf(rs.getString("execution_profile")),
                state, total, queued, running, succeeded, failed,
                rs.getInt("parallelism"), submitted, started, completed,
                throughput, speedup, efficiency);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static final class DurationMath {
        static double secondsBetween(Instant start, Instant end) {
            return java.time.Duration.between(start, end).toNanos() / 1_000_000_000.0;
        }
    }
}