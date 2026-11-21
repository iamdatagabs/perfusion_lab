# Stage 7 — Node-directed distributed execution

## Status

Architecture proposal for the next implementation increment. This document does not claim that node-level dispatch is implemented.

The current platform selects an execution profile (GENERAL, MEMORY or COMPUTE). RabbitMQ then distributes messages among competing consumers of that profile queue. A worker registry exists, but jobs are not assigned to a specific worker node.

## Goal

Introduce explicit, auditable placement of each simulation job onto one registered worker node, while preserving PostgreSQL as the source of truth and RabbitMQ as the transport.

The API/control plane remains separate from worker runtimes. A node agent is the worker process itself; no privileged host daemon is required for the first iteration.

## Target architecture

1. Each worker starts with a stable `WORKER_NODE_ID` (configured explicitly; hostname is a documented fallback). It declares and binds a durable node-specific queue before advertising itself as READY.
2. The worker registers profile, configured slots, declared CPU/RAM, observed telemetry, software version and heartbeat.
3. The scheduler selects a READY node matching the requested profile and live capacity. Selection and slot reservation happen atomically in PostgreSQL.
4. The job row records `target_worker_id`; the transactional outbox event carries the target node and a node-specific routing key.
5. RabbitMQ routes the event only to that node's queue. The worker verifies that the message target matches its own stable identity before claiming the job.
6. Completion, retry, cancellation and lease recovery release or renew the reservation using ownership/fencing tokens.

## Data model changes

Proposed additive migration (V5):

- `worker_nodes`: stable unique `node_id`, lifecycle status (STARTING, READY, DRAINING, OFFLINE), profile, last heartbeat, telemetry timestamp and software version.
- `simulation_jobs`: nullable `target_worker_id`, reservation token and reservation expiry.
- A reservation/assignment record (or equivalent guarded columns) to make slot accounting transactional and auditable.
- Indexes for READY nodes by profile and heartbeat, and jobs by target node/state.

Do not store AUTO as a worker profile. Resolve AUTO to a concrete profile and then to a concrete node before publishing.

## Scheduling and reliability rules

- Reserve capacity with a guarded database update/transaction; never rely on a read-then-write slot count.
- Enforce `active + reserved < configured slots` in the same transaction that assigns the job.
- Exclude stale heartbeats and nodes in DRAINING/OFFLINE.
- Use deterministic tie-breaking after scoring candidates (available slots, CPU pressure, memory pressure, queue age).
- If no node is eligible, keep the job QUEUED and retry placement with bounded backoff; do not silently route it to an arbitrary profile queue.
- Use an outbox event for every assignment. A crash between DB commit and publish must not lose the assignment.
- Delivery remains at-least-once. Worker claim must be idempotent and fenced by reservation/lease token.
- If a node disappears, expire its reservation, mark it OFFLINE after the heartbeat grace period, and reassign only after the previous lease is fenced.
- On graceful shutdown, mark DRAINING, stop accepting assignments, finish or release current work, then mark OFFLINE.
- A restarted node with the same ID must not steal work from an earlier process incarnation; use an incarnation UUID in addition to stable node ID.

## API additions

- `GET /api/v1/cluster/nodes/{nodeId}` — detailed node status and active assignments.
- `POST /api/v1/cluster/nodes/{nodeId}/drain` — authenticated operational action to stop new assignments.
- Extend job/batch status with assigned node, assignment attempt and scheduling explanation.
- Keep existing aggregate capacity endpoint for compatibility.

Administrative endpoints must be protected before deployment outside local development.

## Telemetry boundaries

Report distinct fields for:
- configured capacity (declared CPU, RAM and slots);
- JVM process telemetry;
- cgroup/container limits and usage when available;
- host telemetry only when actually collected by a supported provider.

Unknown metrics must be null/unknown, never fabricated or inferred from JVM heap. Include measurement timestamps and source labels.

## Implementation sequence

1. V5 schema and stable node identity/incarnation model.
2. Node-specific durable queues and registration readiness handshake.
3. Transactional reservation service and placement planner.
4. Target-aware outbox publisher and worker claim validation.
5. Lease expiry, retry/reassignment and graceful drain.
6. Node detail/operations APIs and batch/job assignment metadata.
7. Integration tests with two simulated nodes, queue isolation, race conditions, node loss and duplicate delivery.
8. Compose profile with multiple worker nodes and operational documentation.

## Acceptance criteria

- Two workers of the same profile receive only messages addressed to their own node queues.
- Concurrent schedulers cannot reserve more slots than a node declares.
- An unready, draining or stale node never receives a new assignment.
- A publish crash/retry cannot lose a committed assignment.
- Duplicate delivery cannot execute a job twice concurrently.
- Node loss leads to fenced lease expiry and safe reassignment.
- A restarted node incarnation cannot acknowledge a job owned by its predecessor.
- CI covers reservation races, queue isolation, recovery and duplicate delivery.

## Explicit non-goals for this stage

- Kubernetes orchestration or autoscaling.
- Cross-region federation.
- GPU scheduling or hardware discovery.
- Claims of exactly-once delivery.
- Clinical validation or use in patient care.

## Safety boundary

PerfusionLab remains an educational/research software prototype. Distributed execution changes infrastructure behavior only; it does not validate the physiological model or make outputs suitable for clinical decisions.
