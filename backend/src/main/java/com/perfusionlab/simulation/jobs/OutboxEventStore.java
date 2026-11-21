package com.perfusionlab.simulation.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final RowMapper<OutboxEvent> rowMapper = this::mapRow;

    public OutboxEventStore(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void enqueue(UUID aggregateId, SimulationJobMessage message) {
        try {
            jdbc.update("""
                    INSERT INTO outbox_events (event_id, aggregate_id, event_type, payload, status, created_at, next_attempt_at)
                    VALUES (?, ?, 'SimulationJobRequested', ?::jsonb, 'PENDING', now(), now())
                    """, UUID.randomUUID(), aggregateId, mapper.writeValueAsString(message));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize outbox event", exception);
        }
    }

    public Optional<OutboxEvent> claimNext() {
        return jdbc.query("""
                UPDATE outbox_events
                SET status = 'PROCESSING', locked_until = now() + interval '30 seconds'
                WHERE event_id = (
                    SELECT event_id FROM outbox_events
                    WHERE (status = 'PENDING' AND next_attempt_at <= now())
                       OR (status = 'PROCESSING' AND locked_until < now())
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING event_id, aggregate_id, payload::text, attempts
                """, rowMapper).stream().findFirst();
    }

    public void markPublished(UUID eventId) {
        jdbc.update("""
                UPDATE outbox_events SET status = 'PUBLISHED', published_at = now(),
                    locked_until = NULL, last_error = NULL
                WHERE event_id = ? AND status = 'PROCESSING'
                """, eventId);
    }

    public void markRetry(UUID eventId, int attempts, String error) {
        int nextAttempt = attempts + 1;
        String status = nextAttempt >= 8 ? "DEAD" : "PENDING";
        int delaySeconds = Math.min(300, (int) Math.pow(2, Math.min(nextAttempt, 8)));
        jdbc.update("""
                UPDATE outbox_events
                SET status = ?, attempts = ?, next_attempt_at = now() + (? * interval '1 second'),
                    locked_until = NULL, last_error = ?
                WHERE event_id = ? AND status = 'PROCESSING'
                """, status, nextAttempt, delaySeconds, error, eventId);
    }

    private OutboxEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        try {
            return new OutboxEvent(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("aggregate_id", UUID.class),
                    mapper.readValue(rs.getString("payload"), SimulationJobMessage.class),
                    rs.getInt("attempts"));
        } catch (JsonProcessingException exception) {
            throw new SQLException("Could not deserialize outbox event", exception);
        }
    }

    public record OutboxEvent(UUID eventId, UUID aggregateId, SimulationJobMessage message, int attempts) {}
}