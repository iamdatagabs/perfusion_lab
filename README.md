# PerfusionLab

**Distributed Biomedical Computing Platform for Cardiovascular Surgery & Extracorporeal Circulation Research**

PerfusionLab is an academic software engineering project exploring computational simulation, visualization and reproducible analysis of simplified cardiovascular and cardiopulmonary bypass (CPB/CEC) scenarios.

> **Educational/research prototype only.** It is not a medical device, clinical decision-support system, or substitute for perfusionist/clinical judgment. Its equations and generated values are simplified demonstrations and must not be used to guide patient care.

## Current capabilities

- Responsive Portuguese landing page with interactive educational calculation preview.
- Java 21 + Spring Boot API and separately runnable worker role from the same artifact.
- Asynchronous jobs dispatched through RabbitMQ and executed by competing worker consumers.
- PostgreSQL persistence for job inputs, lifecycle timestamps, states, errors and results.
- Transactional outbox: job row and dispatch event commit in the same PostgreSQL transaction.
- Correlated publisher confirms and mandatory routing checks before an outbox event is marked published.
- Bounded exponential retry for failed outbox publication, ending in DEAD after eight attempts.
- Worker identity, heartbeat and expiring leases, with automatic recovery of stale RUNNING jobs.
- Fenced completion: a worker can update a job only while it still owns that job's current lease.
- Flyway-managed schema and Docker Compose topology with API, worker, PostgreSQL and RabbitMQ.
- Experiment batches with transactional job fan-out and batch progress metrics.
- Three independently routed worker profiles: GENERAL, MEMORY and COMPUTE.
- Worker registry reporting declared CPU/RAM/slots, active jobs, available slots and best-effort JVM/system telemetry.
- Automatic batch profile selection (`AUTO`) using live free slots and observed CPU/heap pressure, with an auditable scheduling reason.
- Stable logical worker identity plus a unique process incarnation UUID.
- Durable per-node RabbitMQ queues and node-specific routing keys, provisioned before the worker advertises itself as READY.
- Optional target node and process-incarnation fields in the message contract; the outbox publisher uses the node route when a target is present.
- Transactional node placement with PostgreSQL row locking; queued target assignments reserve slots until execution starts or recovery clears a stale assignment.

## Architecture

- **DDD:** bounded contexts for Simulation, Perfusion, Physiology, Experiment and Reporting.
- **Hybrid deployment:** API/control plane and worker runtime are separate containers, sharing the same Java artifact and database.
- **EDA:** RabbitMQ direct exchange, durable profile queues and durable node-scoped queues.
- **Transactional outbox:** PostgreSQL atomically stores the job and its event; a publisher relays events to RabbitMQ.
- **At-least-once delivery:** a crash after broker confirmation but before marking the outbox row published can cause duplicate delivery. Atomic worker claims and ownership fencing make repeated messages safe.
- **Horizontal workers:** replicas compete on profile queues for untargeted messages. A targeted message is routed to the durable queue owned by one logical node.
- **Node identity:** a stable configured `WORKER_NODE_ID` identifies the logical node; a fresh `incarnationId` fences messages addressed to a prior process instance.
- **Grid orchestration:** AUTO selects an execution profile; the placement layer then locks an eligible node row and reserves a slot through the job's persisted target assignment.

### Job lifecycle

POST /api/v1/simulations/jobs writes the QUEUED job and SimulationJobRequested outbox event in one transaction. The publisher waits for RabbitMQ confirmation before marking the event PUBLISHED. A worker claims the job, renews its lease while processing, and stores SUCCEEDED or FAILED. If a worker disappears, the API role requeues an expired lease up to three execution attempts.

## Run locally with Docker

Requirements: Docker Engine and Docker Compose.

~~~bash
docker compose -f infra/docker-compose.yml up --build -d
~~~

API: http://localhost:8080

RabbitMQ management: http://localhost:15672 (perfusionlab / perfusionlab_dev)

### Worker pools and cluster inventory

Compose starts three worker pools. GENERAL is the default profile; MEMORY and COMPUTE are separate queues with independently declared resources.

~~~bash
docker compose -f infra/docker-compose.yml up --build -d
docker compose -f infra/docker-compose.yml up -d --scale worker=4
~~~

Each worker provisions a durable queue named `perfusionlab.simulation.node.<WORKER_NODE_ID>` and binds it to `simulation.execute.node.<WORKER_NODE_ID>`. Set a unique `WORKER_NODE_ID` per logical worker node; only use the hostname/profile fallback for single-process or ephemeral local runs. The worker continues listening to its profile queue for backward compatibility while also consuming its node-specific queue.

Inspect active nodes, runtime telemetry and aggregated capacity by profile:

~~~bash
curl http://localhost:8080/api/v1/cluster/nodes
curl http://localhost:8080/api/v1/cluster/capacity
~~~

Configured CPU/RAM/slots are declarations. `systemCpuLoad` is a best-effort system-level measurement; heap values describe the JVM, not total host/container memory. A heartbeat older than two minutes is excluded from scheduling.

### Worker identity and node queue readiness

Each worker process reports a logical `nodeId` and a fresh UUID `incarnationId`. Its runtime `workerId` combines both, so a restarted process cannot reuse the previous process's lease-owner identity. The worker declares its durable node queue and binding before its first READY heartbeat. Set `WORKER_NODE_ID` explicitly when identity must survive container replacement. Node IDs are restricted to letters, digits, underscore and hyphen, with a maximum length of 80 characters.

The message contract accepts optional `targetNodeId` and `targetIncarnationId`. When `targetNodeId` is set, the outbox publisher uses the node-specific routing key; otherwise it preserves the existing profile routing behavior. A worker acknowledges and drops messages addressed to another node or stale process incarnation, preventing obsolete durable-queue messages from being requeued indefinitely. Jobs are pinned only when a live node has free capacity.

### Graceful worker drain

Configure `PERFUSION_ADMIN_TOKEN` on the API service before using the operational endpoint. Drain a specific process incarnation (not merely a reusable node name). Obtain the current `incarnationId` from the node inventory response and replace the example UUID:

~~~bash
curl -X POST http://localhost:8080/api/v1/cluster/nodes/worker-a/drain \\
  -H "Content-Type: application/json" \\
  -H "X-Perfusion-Admin-Token: $PERFUSION_ADMIN_TOKEN" \\
  -d '{"incarnationId":"00000000-0000-0000-0000-000000000000"}'
~~~

The response reports the number of queued assignments returned to profile queues and the number of running jobs still draining. A node becomes OFFLINE automatically when no running jobs remain. The endpoint is disabled when no admin token is configured. Configure the token as a secret; do not commit it.

### Benchmark

~~~bash
python scripts/benchmark_workers.py --workers 1 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 2 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 4 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 8 --jobs 100 --concurrency 16
~~~

The script reports successful/failed jobs, wall time, throughput and p50/p95 completion latency. These are software throughput measurements, not clinical performance metrics.

### Submit an experiment batch

~~~bash
curl -X POST http://localhost:8080/api/v1/experiments/batches \
  -H "Content-Type: application/json" \
  -d '{
    "executionProfile": "AUTO",
    "parallelism": 1,
    "simulations": [
      {
        "pumpFlowLMin": 4.8,
        "hemoglobinGdL": 10.0,
        "arterialSaturation": 0.98,
        "systemicVascularResistance": 1200.0,
        "bodySurfaceAreaM2": 1.9,
        "temperatureC": 35.5
      }
    ]
  }'
~~~

Poll the returned batch URL at GET /api/v1/experiments/batches/{batchId}. `executionProfile` accepts AUTO, GENERAL, MEMORY or COMPUTE. AUTO chooses an eligible profile using active worker slots and best-effort CPU/heap telemetry; the response records both the requested profile and the resolved profile/reason. If no live free slots exist, AUTO queues on GENERAL as a documented fallback. Optional baselineSequentialMs enables speedup and parallel-efficiency calculations; without a measured sequential baseline, these fields remain null.

Run a batch benchmark:

~~~bash
python scripts/benchmark_grid.py --profile AUTO --jobs 100 --parallelism 2
python scripts/benchmark_grid.py --profile COMPUTE --jobs 100 --parallelism 2
python scripts/benchmark_grid.py --profile GENERAL --jobs 100 --parallelism 4 --baseline-sequential-ms 25000
~~~

## API

- GET /api/v1/health
- POST /api/v1/simulations/preview — synchronous educational preview.
- POST /api/v1/simulations/jobs — enqueue an asynchronous job; returns HTTP 202 and a job ID.
- GET /api/v1/simulations/jobs/{jobId} — inspect QUEUED, RUNNING, SUCCEEDED or FAILED state and result.
- POST /api/v1/experiments/batches — atomically create a batch and all its simulation jobs.
- GET /api/v1/experiments/batches/{batchId} — inspect progress, throughput, optional speedup and efficiency.
- GET /api/v1/cluster/nodes — inspect active worker nodes, declared capacity and runtime telemetry.
- GET /api/v1/cluster/capacity — inspect aggregated capacity and observed pressure by execution profile.
- GET /actuator/metrics/perfusionlab.worker.messages — inspect worker outcomes by profile.
- GET /actuator/metrics/perfusionlab.worker.execution — inspect worker execution latency.
- GET /actuator/metrics/perfusionlab.outbox.events — inspect outbox publication, retry and dead-letter outcomes.
- GET /actuator/metrics/perfusionlab.rabbitmq.dead_letter.messages — inspect the RabbitMQ dead-letter queue depth.
- Jaeger UI at http://localhost:16686 — inspect distributed traces when running the local Compose stack.
- POST /api/v1/cluster/nodes/{nodeId}/drain — authenticated graceful drain; reassigns queued jobs and lets running jobs finish.

## Reliability semantics and limitations

- Delivery is **at least once**, not exactly once. Consumers must remain idempotent.
- Outbox events retry with bounded exponential backoff; events reaching eight publication attempts enter DEAD and require operational inspection/replay.
- Worker leases expire after two minutes and are extended every 30 seconds. Three expired execution attempts result in FAILED.
- Recovery runs in the API role. Multiple API instances are safe because outbox claims and recovery use PostgreSQL row locks with SKIP LOCKED.
- The heartbeat is worker-scoped rather than task-scoped. A future iteration should add per-job heartbeats, cancellation and stronger fencing for long-running tasks.
- AUTO selects a profile, then node placement reserves a concrete node when live capacity is available. If no node is eligible, the job falls back to the profile queue. Node drain reassigns queued jobs to profile queues and allows already-running work to finish; the response exposes running and remaining queued assignment counts. CPU load is best-effort system telemetry and JVM heap metrics are not cgroup/host memory telemetry. Worker profile queues and node queues dead-letter rejected deliveries to the durable `perfusionlab.simulation.dead` queue. The listener does not automatically requeue rejected/poison messages; the explicit `ImmediateRequeueAmqpException` path remains reserved for temporary shared-queue handoff during node drain. Inspect the queue in the RabbitMQ management UI and manually redrive only after correcting the cause, publishing the original message to the matching `simulation.execute.{profile}` routing key on `perfusionlab.simulation`. The outbox also has its own terminal `DEAD` state after eight publication attempts; this is separate from RabbitMQ's consumer dead-letter queue. Metrics are exposed through Spring Boot Actuator. 
- Local Compose credentials are development-only and must be replaced for shared or deployed environments.
- The educational model is not clinically validated and must never guide patient care.

## Author

**Gabriel Passos de Jesus** — geographer, computer scientist and university lecturer, with a master's degree in Computer Science and Engineering from UEPG and ongoing professional master's studies in Medicine at UEM. Works across software engineering, data science, geotechnologies and digital health, with interests in computational methods for healthcare and technical expertise.

GitHub: [@iamdatagabs](https://github.com/iamdatagabs)

## License

Apache-2.0.


### Atomic node placement and slot reservations

New jobs first attempt to reserve a live READY node in the selected execution profile. PostgreSQL locks the candidate worker row with `FOR UPDATE SKIP LOCKED`; within the same transaction, the job is inserted with the selected `target_node_id` and `target_incarnation_id`, and its outbox event carries the same target. A queued targeted job counts as a reserved slot; once running, that same row continues to count as occupied capacity. This avoids a separate counter that could drift after crashes.

The scheduler prefers nodes with fewer assigned QUEUED/RUNNING jobs, then lower best-effort system CPU load. If no eligible node has a free slot, the job remains unpinned and uses the existing profile queue. If a worker lease expires, recovery clears the old node pin and emits a profile-routed retry, allowing a fresh process incarnation to claim the job. Delivery remains at least once and job claiming remains guarded by the database state transition.


## Distributed tracing and correlation

The API accepts an optional `X-Correlation-ID` request header. Safe values are echoed in the response and included in application log context; missing or invalid values are replaced with a generated UUID. A correlation ID is shared by all jobs submitted in the same experiment batch.

Each persisted job stores its correlation ID and W3C `traceparent`. The transactional outbox retains that context through retries and lease recovery. The publisher creates an outbox span and injects its child context into the RabbitMQ message; the worker extracts it and creates a simulation execution span. Spans carry searchable `correlation.id` and `simulation.job_id` attributes.

The local Compose stack includes Jaeger with OTLP/HTTP ingestion:
- UI: http://localhost:16686
- OTLP/HTTP: http://localhost:4318/v1/traces
- Set `OTEL_EXPORTER_OTLP_ENDPOINT` to your collector endpoint outside Compose.
- Sampling defaults to 100% for local development; set `TRACING_SAMPLING_PROBABILITY` appropriately for shared or production deployments.

Example request:

~~~bash
curl -i -X POST http://localhost:8080/api/v1/simulations/jobs \\
  -H 'Content-Type: application/json' \\
  -H 'X-Correlation-ID: perfusion-experiment-2026-001' \\
  -d '{"pumpFlowLMin":4.8,"systemicVascularResistance":10.0,"arterialSaturation":0.98,"hemoglobinGdL":12.0,"bodySurfaceAreaM2":1.9,"temperatureC":35.5}'
~~~

## Reliability / chaos checks

The backend test suite includes a simulated RabbitMQ outage: a failed publish must return the outbox event to retry scheduling rather than lose it. Lease recovery tests verify trace context is retained when work is reassigned, and existing store tests cover competing worker claims. These are deterministic fault-injection unit tests; they do not replace multi-container integration or production chaos experiments.
