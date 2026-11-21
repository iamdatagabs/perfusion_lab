CREATE TABLE IF NOT EXISTS simulation_jobs (
    job_id UUID PRIMARY KEY,
    state VARCHAR(16) NOT NULL CHECK (state IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    submitted_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    input_json JSONB NOT NULL,
    result_json JSONB,
    error TEXT,
    CONSTRAINT simulation_jobs_state_result_check CHECK (
        (state = 'SUCCEEDED' AND result_json IS NOT NULL)
        OR state <> 'SUCCEEDED'
    )
);

CREATE INDEX IF NOT EXISTS idx_simulation_jobs_state_submitted
    ON simulation_jobs (state, submitted_at);
