# PerfusionLab — Architecture

## Runtime topology

The API and worker are separate runtime roles built from the same Java 21 / Spring Boot artifact. They communicate through RabbitMQ and share PostgreSQL as the durable job registry and outbox.

~~~mermaid
flowchart LR
  Client[Research Console / API Client] --> API[API container]
  API --> Planner[Execution planner]
  Planner --> DB[(PostgreSQL)]
  API --> DB
  API --> O[(Transactional Outbox)]
  O --> PUB[Outbox publisher]
  PUB -->|confirmed publish| EX[RabbitMQ exchange]
  EX --> G[GENERAL queue]
  EX --> M[MEMORY queue]
  EX --> C[COMPUTE queue]
  G --> W1[GENERAL workers]
  M --> W2[MEMORY workers]
  C --> WN[COMPUTE workers]
  W1 --> DB
  W2 --> DB
  WN --> DB
  API -->|lease recovery| DB
  API -->|poll job state| DB
~~~

Set PERFUSION_ROLE=api for the HTTP process and PERFUSION_ROLE=worker for consumers. The API role owns outbox publishing and stale-lease recovery. Worker containers use SPRING_MAIN_WEB_APPLICATION_TYPE=none.

## Transactional outbox

1. API inserts a QUEUED simulation_jobs row and a SimulationJobRequested row in outbox_events within one PostgreSQL transaction.
2. The scheduled publisher claims one eligible event using FOR UPDATE SKIP LOCKED, setting a short processing lease.
3. It publishes with RabbitMQ correlated publisher confirms and mandatory routing.
4. Only a positive broker confirmation and no returned message allow the event to become PUBLISHED.
5. On failure, the event returns to PENDING with exponential backoff; after eight attempts it becomes DEAD for operator review.

This closes the database-commit/message-publish gap. It does not provide exactly-once delivery: a process can receive a positive broker confirmation and crash before committing PUBLISHED. That event can be sent again after its outbox lock expires. Consumers therefore must be idempotent.

## Job lifecycle, leases and recovery

States: QUEUED → RUNNING → SUCCEEDED | FAILED.

A worker claims a job using an atomic UPDATE ... WHERE state = 'QUEUED', records its worker UUID, increments attempt_count, and sets a two-minute lease. A heartbeat every 30 seconds extends the lease for jobs owned by that worker. Completion is fenced by worker_id, so a worker whose lease was reclaimed cannot overwrite a newer attempt.

The API role scans expired RUNNING leases every 15 seconds. It requeues attempts below three and inserts a new outbox event in the same transaction; jobs that exhaust three expired execution attempts become FAILED. Recovery is safe across multiple API replicas through PostgreSQL row locking with SKIP LOCKED.

The heartbeat is process-level rather than tied to an individual task. A later iteration should use per-job heartbeat and cancellation to guard long-running or paused tasks.

## Persistence model

simulation_jobs stores job input, output, state, timestamps, worker identity, heartbeat, lease expiry and execution attempt count.

outbox_events stores event payload, aggregate ID, publication state, attempt count, retry schedule, lock expiry, publication timestamp and sanitized last error.

Flyway manages all schema changes.

## Grid profiles, batch orchestration and capacity registry

The API accepts an experiment batch with up to 1,000 input scenarios, a requested execution profile (AUTO, GENERAL, MEMORY or COMPUTE) and expected parallelism. AUTO evaluates fresh worker heartbeats, available slots, best-effort system CPU load and JVM heap pressure, then resolves to one concrete profile before persistence. The selected profile and decision reason are stored with the batch. If no live free slots are reported, AUTO falls back to GENERAL so work can remain queued. The API inserts the batch, all job rows and all outbox events in one database transaction. Each message carries its resolved profile and the outbox publisher routes it to one of three durable queues:

- GENERAL: default lightweight tasks; retains the legacy queue name for compatibility.
- MEMORY: tasks expected to need larger memory capacity.
- COMPUTE: CPU-oriented tasks.

Workers subscribe only to their configured profile queue. Compose provisions one example worker for each profile; each node declares CPU cores, RAM and concurrency slots through environment variables. The worker registry stores these declarations and heartbeats. It also samples system CPU load where the JVM exposes it and JVM heap used/max values. The inventory endpoint reports active jobs and available slots; `/api/v1/cluster/capacity` aggregates capacity and telemetry by profile. Nodes with heartbeats older than two minutes are considered inactive.

This is profile-level resource-aware scheduling, not node pinning: the planner selects a profile queue and RabbitMQ distributes work among consumers attached to that queue. CPU load is a best-effort system-level value; heap values describe the JVM and must not be interpreted as physical or cgroup memory. The registry does not yet provide per-container CPU/memory telemetry or direct multi-host agent control.

Batch status aggregates queued/running/succeeded/failed jobs and reports throughput. Speedup and parallel efficiency are computed only when the caller supplies a measured sequential baseline:

- speedup = sequential baseline duration / observed batch wall time
- parallel efficiency = speedup / declared parallelism

These metrics describe software execution only. Declared worker resources are not hardware telemetry, and tiny educational calculations may be too cheap to produce meaningful cluster scaling results.

## Horizontal worker pool and benchmark

Workers are competing consumers on one durable queue. This is a useful horizontal-scaling experiment, but it is not yet a grid scheduler. scripts/benchmark_workers.py reports throughput, p50/p95 completion latency and terminal job counts. scripts/benchmark_grid.py submits batch experiments and reports batch throughput, optional speedup and parallel efficiency. Record actual CPU, memory, database and broker utilization separately before drawing scaling conclusions.

## Remaining work

- Add a dedicated broker dead-letter queue and operator tooling to inspect/replay DEAD outbox events.
- Add per-job heartbeat, cancellation and execution fencing for long-running tasks.
- Add distributed tracing, structured logs, alerting and queue-age metrics.
- Add experiment batches, model version pinning, random seeds and checksums.
- Add container-aware CPU/memory telemetry, direct node-level placement and multi-host agent registration with capability negotiation.

## Bounded contexts

- **Simulation:** job lifecycle, model versioning, execution orchestration.
- **Perfusion:** extracorporeal circuit configuration.
- **Physiology:** physiological variables and validated model interfaces.
- **Experiment:** parameter sweeps, reproducibility metadata and benchmarks.
- **Reporting:** analysis, plots and exportable research artifacts.

## Safety and research scope

PerfusionLab is an academic software prototype. Current outputs are not clinically validated, are not a medical device, and must not be used for patient care, perfusion management, diagnosis or treatment decisions.
