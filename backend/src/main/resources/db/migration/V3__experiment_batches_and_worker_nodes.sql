CREATE TABLE experiment_batches (
    batch_id UUID PRIMARY KEY,
    execution_profile VARCHAR(16) NOT NULL CHECK (execution_profile IN ('GENERAL', 'MEMORY', 'COMPUTE')),
    parallelism INTEGER NOT NULL CHECK (parallelism BETWEEN 1 AND 256),
    job_count INTEGER NOT NULL CHECK (job_count BETWEEN 1 AND 1000),
    baseline_sequential_ms BIGINT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE simulation_jobs
    ADD COLUMN IF NOT EXISTS batch_id UUID REFERENCES experiment_batches(batch_id),
    ADD COLUMN IF NOT EXISTS execution_profile VARCHAR(16) NOT NULL DEFAULT 'GENERAL'
        CHECK (execution_profile IN ('GENERAL', 'MEMORY', 'COMPUTE'));

CREATE INDEX IF NOT EXISTS idx_simulation_jobs_batch_state
    ON simulation_jobs (batch_id, state);

CREATE INDEX IF NOT EXISTS idx_simulation_jobs_profile_state
    ON simulation_jobs (execution_profile, state, submitted_at);

CREATE TABLE worker_nodes (
    worker_id VARCHAR(128) PRIMARY KEY,
    execution_profile VARCHAR(16) NOT NULL CHECK (execution_profile IN ('GENERAL', 'MEMORY', 'COMPUTE')),
    hostname VARCHAR(255) NOT NULL,
    cpu_cores INTEGER NOT NULL CHECK (cpu_cores > 0),
    memory_mb INTEGER NOT NULL CHECK (memory_mb > 0),
    slots INTEGER NOT NULL CHECK (slots > 0),
    started_at TIMESTAMPTZ NOT NULL,
    heartbeat_at TIMESTAMPTZ NOT NULL,
    software_version VARCHAR(64) NOT NULL
);

CREATE INDEX idx_worker_nodes_profile_heartbeat
    ON worker_nodes (execution_profile, heartbeat_at DESC);
