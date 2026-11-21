ALTER TABLE worker_nodes
    ADD COLUMN node_id VARCHAR(96),
    ADD COLUMN incarnation_id UUID,
    ADD COLUMN node_status VARCHAR(16) NOT NULL DEFAULT 'READY'
        CHECK (node_status IN ('STARTING', 'READY', 'DRAINING', 'OFFLINE'));

UPDATE worker_nodes
SET node_id = LEFT(worker_id, 96),
    incarnation_id = '00000000-0000-0000-0000-000000000000'::UUID
WHERE node_id IS NULL OR incarnation_id IS NULL;

ALTER TABLE worker_nodes
    ALTER COLUMN node_id SET NOT NULL,
    ALTER COLUMN incarnation_id SET NOT NULL;

CREATE INDEX idx_worker_nodes_node_id_heartbeat
    ON worker_nodes (node_id, heartbeat_at DESC);
CREATE INDEX idx_worker_nodes_status_profile_heartbeat
    ON worker_nodes (node_status, execution_profile, heartbeat_at DESC);
