ALTER TABLE simulation_jobs
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN traceparent VARCHAR(128);

UPDATE simulation_jobs
SET correlation_id = job_id::text
WHERE correlation_id IS NULL;

ALTER TABLE simulation_jobs
    ALTER COLUMN correlation_id SET NOT NULL;

CREATE INDEX idx_simulation_jobs_correlation_id
    ON simulation_jobs (correlation_id);
