# PerfusionLab

**Distributed Biomedical Computing Platform for Cardiovascular Surgery & Extracorporeal Circulation Research**

PerfusionLab is an academic software engineering project exploring computational simulation, visualization and reproducible analysis of simplified cardiovascular and cardiopulmonary bypass (CPB/CEC) scenarios.

> **Educational/research prototype only.** It is not a medical device, clinical decision-support system, or substitute for perfusionist/clinical judgment. Its equations and generated values are simplified demonstrations and must not be used to guide patient care.

## Current capabilities

- Responsive Portuguese landing page with interactive educational calculation preview.
- Java 21 + Spring Boot API and a separately runnable worker role from the same artifact.
- Asynchronous jobs dispatched through RabbitMQ and executed by competing worker consumers.
- PostgreSQL persistence for job inputs, lifecycle timestamps, states, errors and results.
- Flyway-managed database schema.
- Atomic QUEUED → RUNNING claim to avoid concurrent duplicate execution of the same queued job.
- Docker Compose topology with API, worker, PostgreSQL and RabbitMQ.
- Repeatable HTTP benchmark script for comparing worker pool sizes.

## Architecture

- **DDD:** bounded contexts for Simulation, Perfusion, Physiology, Experiment and Reporting.
- **Hybrid deployment:** API/control plane and worker runtime are separate containers, sharing the same Java artifact and database.
- **EDA:** RabbitMQ direct exchange, durable queue and JSON message contract.
- **Horizontal workers:** run multiple worker replicas as competing consumers.
- **Grid-computing direction:** benchmark and scheduling foundation is present; multi-host resource-aware scheduling is a future step.

### Job lifecycle

POST /api/v1/simulations/jobs persists a job as QUEUED, then publishes its message. A worker claims it, changes it to RUNNING, executes the model and persists SUCCEEDED or FAILED. The API reads the durable state from PostgreSQL.

## Run locally with Docker

Requirements: Docker Engine and Docker Compose.

~~~bash
docker compose -f infra/docker-compose.yml up --build -d
~~~

API: http://localhost:8080

RabbitMQ management: http://localhost:15672 (perfusionlab / perfusionlab_dev)

The first startup builds the Java image, starts PostgreSQL and RabbitMQ, runs Flyway migrations, then starts the API and one worker.

### Scale the worker pool

~~~bash
docker compose -f infra/docker-compose.yml up --build -d --scale worker=4
~~~

Use 1, 2, 4 or 8 worker replicas for a first scaling experiment. The API remains a single instance in this local topology.

### Benchmark

Run one benchmark for each worker count after setting the desired replica count:

~~~bash
python scripts/benchmark_workers.py --workers 1 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 2 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 4 --jobs 100 --concurrency 16
python scripts/benchmark_workers.py --workers 8 --jobs 100 --concurrency 16
~~~

The script reports successful/failed jobs, wall time, throughput and p50/p95 completion latency. For meaningful comparisons, use the same machine, job count, concurrency, image build and warm-up procedure; repeat each run and compare medians. These are software throughput measurements, not clinical performance metrics.

## API

- GET /api/v1/health
- POST /api/v1/simulations/preview — synchronous educational preview.
- POST /api/v1/simulations/jobs — enqueue an asynchronous job; returns HTTP 202 and a job ID.
- GET /api/v1/simulations/jobs/{jobId} — inspect QUEUED, RUNNING, SUCCEEDED or FAILED state and result.

Example request:

~~~json
{
  "pumpFlowLMin": 4.8,
  "hemoglobinGdL": 10.0,
  "arterialSaturation": 0.98,
  "systemicVascularResistance": 1200.0,
  "bodySurfaceAreaM2": 1.9,
  "temperatureC": 35.5
}
~~~

## Important reliability limits

- Job creation and RabbitMQ publication are not yet coordinated by a transactional outbox. A process crash between database commit and message publication can leave a queued job undispatched.
- A worker crash after claiming a job can leave it in RUNNING; leases, heartbeats and stale-job recovery are not implemented yet.
- Retry policy, dead-letter queue and operational alerting remain future work.
- Worker replicas compete on one RabbitMQ queue; there is not yet a cross-host grid scheduler or heterogeneous resource placement.
- PostgreSQL and RabbitMQ local credentials are development-only and must be replaced for shared or deployed environments.
- The educational model is not clinically validated and must never guide patient care.

## Author

**Gabriel Passos de Jesus** — geographer, computer scientist and university lecturer, with a master's degree in Computer Science and Engineering from UEPG and ongoing professional master's studies in Medicine at UEM. Works across software engineering, data science, geotechnologies and digital health, with interests in computational methods for healthcare and technical expertise.

GitHub: [@iamdatagabs](https://github.com/iamdatagabs)

## License

Apache-2.0.
