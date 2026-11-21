# PerfusionLab — Architecture

## Runtime topology

The API and worker are separate runtime roles built from the same Java 21 / Spring Boot artifact. They communicate through RabbitMQ and share PostgreSQL as the durable job registry.

~~~mermaid
flowchart LR
  Client[Research Console / API Client] --> API[API container]
  API --> DB[(PostgreSQL)]
  API -->|publish JSON job| EX[RabbitMQ direct exchange]
  EX --> Q[Durable simulation queue]
  Q --> W1[Worker replica 1]
  Q --> W2[Worker replica 2]
  Q --> WN[Worker replica N]
  W1 --> DB
  W2 --> DB
  WN --> DB
  API -->|poll job state| DB
~~~

Set PERFUSION_ROLE=api for the HTTP process and PERFUSION_ROLE=worker for consumers. Worker containers use SPRING_MAIN_WEB_APPLICATION_TYPE=none. Compose starts one worker by default; --scale worker=N starts N competing consumers.

## Job lifecycle and concurrency

1. API validates the request and inserts a QUEUED row containing the immutable input JSON and submission timestamp.
2. API publishes a JSON message to the durable RabbitMQ queue.
3. A worker atomically claims the job with UPDATE ... WHERE state = 'QUEUED'. Only one consumer can change that row to RUNNING.
4. The worker executes the selected model version and persists SUCCEEDED plus result JSON, or FAILED plus a sanitized error class.
5. API reads current state and result from PostgreSQL.

States: QUEUED → RUNNING → SUCCEEDED | FAILED.

The compare-and-set claim prevents duplicate deliveries from concurrently executing a job. It does not provide exactly-once execution or recovery after worker loss.

## Persistence model

simulation_jobs stores:
- job_id: UUID primary key.
- state: constrained lifecycle state.
- submitted_at, started_at, completed_at: UTC timestamps.
- input_json: submitted input for traceability.
- result_json: output payload on success.
- error: sanitized exception class on failure.

Flyway manages the schema. Both API and worker use the same PostgreSQL database.

## Horizontal worker pool and benchmark

Workers are competing consumers on one durable queue. This is a useful first horizontal-scaling experiment, but it is not yet a grid scheduler. The benchmark helper at scripts/benchmark_workers.py submits a fixed workload and reports throughput, p50/p95 completion latency and terminal job counts.

Run with worker pool sizes 1, 2, 4 and 8 on the same host and compare repeated runs. Record CPU, memory, database and broker utilization separately before drawing scaling conclusions.

## Reliability gaps to close

- **Transactional outbox:** currently, job insertion and broker publication are separate operations. A crash between them may strand a QUEUED row.
- **Leases and heartbeats:** a worker crash after claiming a job can leave it RUNNING; no stale-job reaper exists.
- **Retry and dead-letter policy:** not yet configured. The current worker records model exceptions as FAILED.
- **Observability:** job-level traces, worker identity, queue age and structured metrics need a later increment.
- **Security:** local Compose credentials are development-only.

## Next distributed-computing increment

1. Add transactional outbox and publisher confirms.
2. Add worker leases, heartbeat, timeout and stale-job recovery.
3. Add bounded retry and dead-letter queue.
4. Add experiment batches, model version pinning, random seeds and checksums.
5. Add a scheduler for resource-aware placement across local or cloud nodes.
6. Benchmark 1, 2, 4 and 8 workers and report speedup, efficiency, throughput and tail latency.

## Bounded contexts

- **Simulation:** job lifecycle, model versioning, execution orchestration.
- **Perfusion:** extracorporeal circuit configuration.
- **Physiology:** physiological variables and validated model interfaces.
- **Experiment:** parameter sweeps, reproducibility metadata and benchmarks.
- **Reporting:** analysis, plots and exportable research artifacts.

## Safety and research scope

PerfusionLab is an academic software prototype. Current outputs are not clinically validated, are not a medical device, and must not be used for patient care, perfusion management, diagnosis or treatment decisions.
