# PerfusionLab — Architecture

## Current implementation

The project begins as a modular Spring Boot application with explicit domain boundaries. The HTTP API acts as the control plane; simulation jobs are sent to RabbitMQ and consumed by a worker component.

~~~mermaid
flowchart LR
  Client[Research Console / API Client] --> API[Spring Boot API]
  API -->|publish JSON job| Exchange[RabbitMQ Direct Exchange]
  Exchange --> Queue[Durable Simulation Queue]
  Queue --> Worker[Simulation Worker]
  Worker --> Model[Simplified Educational Model]
  Worker --> Store[In-memory Job Registry]
  Client -->|poll job status| API
  API --> Store
~~~

## Job lifecycle

1. Client submits a validated simulation input to POST /api/v1/simulations/jobs.
2. API creates a UUID and records QUEUED.
3. API publishes a JSON message to the durable RabbitMQ queue.
4. Worker marks the job RUNNING, executes the model and stores the result as SUCCEEDED; failures are marked FAILED.
5. Client polls GET /api/v1/simulations/jobs/{jobId}.

States: QUEUED → RUNNING → SUCCEEDED | FAILED.

## Important current limits

- Job status and results live in process memory; restarting the API loses them.
- RabbitMQ queue is durable, but the job registry is not. A broker acknowledgement after process loss can leave state inconsistent.
- API and worker are components in one deployment, not independently deployed services yet.
- No distributed scheduler, worker lease, retry policy, dead-letter queue, or exactly-once guarantee exists yet.
- PostgreSQL is provisioned but not yet used for job persistence.
- The simplified equations are illustrative only and have not been clinically validated.

## Bounded contexts

- **Simulation:** job lifecycle, model versioning, execution orchestration.
- **Perfusion:** future domain model for extracorporeal circuit configuration.
- **Physiology:** future validated physiological variables and model interfaces.
- **Experiment:** parameter sweeps, reproducibility metadata and benchmark runs.
- **Reporting:** analysis, plots and exportable research artifacts.

## Evolution path

1. Persist job state and results in PostgreSQL; introduce transactional outbox for reliable dispatch.
2. Extract worker into its own deployable service and scale replicas horizontally.
3. Add idempotency keys, bounded retries, dead-letter queue, execution timeout and worker heartbeat/leases.
4. Add scheduler for parameter sweeps and resource-aware placement across worker nodes.
5. Benchmark 1, 2, 4 and 8 workers with reproducible workloads; report throughput, latency and failure rate.
6. Only after independent scientific validation, evaluate richer physiological models.

## Grid computing direction

Each job should become a self-contained immutable workload containing input, model version, experiment identifier, seed and resource requirements. The scheduler can then distribute jobs to workers across a local server pool or cloud instances. A future implementation should record node identity, start/end timestamps, runtime, software version and checksum for reproducibility.

## Safety and research scope

PerfusionLab is an academic software prototype. Current outputs are not clinically validated, are not a medical device, and must not be used for patient care, perfusion management, diagnosis or treatment decisions.
