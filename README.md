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
- HTTP benchmark script for comparing worker pool sizes.

## Architecture

- **DDD:** bounded contexts for Simulation, Perfusion, Physiology, Experiment and Reporting.
- **Hybrid deployment:** API/control plane and worker runtime are separate containers, sharing the same Java artifact and database.
- **EDA:** RabbitMQ direct exchange, durable queue and JSON message contract.
- **Transactional outbox:** PostgreSQL atomically stores the job and its event; a publisher relays events to RabbitMQ.
- **At-least-once delivery:** a crash after broker confirmation but before marking the outbox row published can cause duplicate delivery. Atomic worker claims and ownership fencing make repeated messages safe.
- **Horizontal workers:** replicas compete on one queue; stale leases are recovered by the API role.
- **Grid-computing direction:** multi-host resource-aware scheduling remains future work.

### Job lifecycle

POST /api/v1/simulations/jobs writes the QUEUED job and SimulationJobRequested outbox event in one transaction. The publisher waits for RabbitMQ confirmation before marking the event PUBLISHED. A worker claims the job, renews its lease while processing, and stores SUCCEEDED or FAILED. If a worker disappears, the API role requeues an expired lease up to three execution attempts.

## Run locally with Docker

Requirements: Docker Engine and Docker Compose.

~~~bash
docker compose -f infra/docker-compose.yml up --build -d
~~~

API: http://localhost:8080

RabbitMQ management: http://localhost:15672 (perfusionlab / perfusionlab_dev)

### Scale the worker pool

~~~bash
docker compose -f infra/docker-compose.yml up --build -d --scale worker=4
~~~

### Benchmark

~~~bash
python scripts/benchmark_workers.py --workers 1 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 2 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 4 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 8 --jobs 100 --concurrency 16
~~~

The script reports successful/failed jobs, wall time, throughput and p50/p95 completion latency. These are software throughput measurements, not clinical performance metrics.

## API

- GET /api/v1/health
- POST /api/v1/simulations/preview — synchronous educational preview.
- POST /api/v1/simulations/jobs — enqueue an asynchronous job; returns HTTP 202 and a job ID.
- GET /api/v1/simulations/jobs/{jobId} — inspect QUEUED, RUNNING, SUCCEEDED or FAILED state and result.

## Reliability semantics and limitations

- Delivery is **at least once**, not exactly once. Consumers must remain idempotent.
- Outbox events retry with bounded exponential backoff; events reaching eight publication attempts enter DEAD and require operational inspection/replay.
- Worker leases expire after two minutes and are extended every 30 seconds. Three expired execution attempts result in FAILED.
- Recovery runs in the API role. Multiple API instances are safe because outbox claims and recovery use PostgreSQL row locks with SKIP LOCKED.
- The heartbeat is worker-scoped rather than task-scoped. A future iteration should add per-job heartbeats, cancellation and stronger fencing for long-running tasks.
- There is not yet a dedicated dead-letter queue for broker-rejected messages, distributed tracing, alerting, or resource-aware grid scheduling.
- Local Compose credentials are development-only and must be replaced for shared or deployed environments.
- The educational model is not clinically validated and must never guide patient care.

## Author

**Gabriel Passos de Jesus** — geographer, computer scientist and university lecturer, with a master's degree in Computer Science and Engineering from UEPG and ongoing professional master's studies in Medicine at UEM. Works across software engineering, data science, geotechnologies and digital health, with interests in computational methods for healthcare and technical expertise.

GitHub: [@iamdatagabs](https://github.com/iamdatagabs)

## License

Apache-2.0.
