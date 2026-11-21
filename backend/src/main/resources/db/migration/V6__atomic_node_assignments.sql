ALTER TABLE simulation_jobs
    ADD COLUMN target_node_id VARCHAR(96),
    ADD COLUMN target_incarnation_id UUID;

CREATE INDEX idx_simulation_jobs_node_assignment
    ON simulation_jobs (target_node_id, target_incarnation_id, state)
    WHERE target_node_id IS NOT NULL;

-- A queued job with a target is a durable capacity reservation. Once the job
-- starts, the same row continues to account for the occupied slot.
