ALTER TABLE experiment_batches
    ADD COLUMN requested_execution_profile VARCHAR(16) NOT NULL DEFAULT 'GENERAL'
        CHECK (requested_execution_profile IN ('AUTO', 'GENERAL', 'MEMORY', 'COMPUTE')),
    ADD COLUMN scheduling_reason VARCHAR(128) NOT NULL DEFAULT 'LEGACY_GENERAL';

ALTER TABLE worker_nodes
    ADD COLUMN system_cpu_load DOUBLE PRECISION,
    ADD COLUMN jvm_heap_used_mb BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN jvm_heap_max_mb BIGINT NOT NULL DEFAULT 0;

CREATE INDEX idx_worker_nodes_telemetry
    ON worker_nodes (execution_profile, heartbeat_at DESC, system_cpu_load);
