# PerfusionLab

**Distributed Biomedical Computing Platform for Cardiovascular Surgery & Extracorporeal Circulation Research**

PerfusionLab is an academic software engineering project exploring computational simulation, visualization and reproducible analysis of simplified cardiovascular and cardiopulmonary bypass (CPB/CEC) scenarios.

> **Educational/research prototype only.** It is not a medical device, clinical decision-support system, or substitute for perfusionist/clinical judgment. Its equations and generated values are simplified demonstrations and must not be used to guide patient care.

## Current capabilities

- Responsive Portuguese landing page with interactive educational calculation preview.
- Java 21 + Spring Boot API.
- Deterministic, simplified preview of estimated MAP and oxygen delivery.
- Asynchronous simulation jobs dispatched through a durable RabbitMQ queue.
- Worker consumer that executes the educational model and exposes job state/result.
- PostgreSQL + RabbitMQ local infrastructure (PostgreSQL is reserved for the persistence increment).
- DDD-oriented modular structure and documented scale-out path.

## Architecture direction

- **DDD:** bounded contexts for Simulation, Perfusion, Physiology, Experiment and Reporting.
- **Hybrid deployment:** API/control plane and worker are logically separated; they currently run in one Spring Boot deployment and can be split into independent services later.
- **EDA:** RabbitMQ direct exchange, durable queue and JSON message contract for simulation jobs.
- **Grid computing:** the job contract is a foundation for multiple worker replicas/nodes. Scheduling, leases, distributed persistence and benchmark coordination are future increments.

The current job registry is in-memory and therefore resets when the API restarts. RabbitMQ provides durable message queuing, but the job state/result is not yet persisted. Configure idempotency and durable result storage before production or multi-node operation.

## Run locally

Requirements: Java 21+, Maven 3.9+, Docker Compose.

~~~bash
docker compose -f infra/docker-compose.yml up -d
cd backend
mvn spring-boot:run
~~~

API:
- GET /api/v1/health
- POST /api/v1/simulations/preview — synchronous educational preview.
- POST /api/v1/simulations/jobs — enqueue an asynchronous job; returns HTTP 202 and a job ID.
- GET /api/v1/simulations/jobs/{jobId} — inspect QUEUED, RUNNING, SUCCEEDED or FAILED state and result.

Example job request:

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

The result is a mathematical demonstration, not a clinical target, interpretation or recommendation.

## Local services

- RabbitMQ AMQP: localhost:5672
- RabbitMQ management UI: http://localhost:15672 (perfusionlab / perfusionlab_dev)
- PostgreSQL: localhost:5432 (perfusionlab / perfusionlab_dev)

## GitHub Pages

The landing page is a standalone index.html. Publish it from the default branch using the repository's GitHub Actions Pages workflow.

## Author

**Gabriel Passos de Jesus** — geographer, computer scientist and university lecturer, with a master's degree in Computer Science and Engineering from UEPG and ongoing professional master's studies in Medicine at UEM. Works across software engineering, data science, geotechnologies and digital health, with interests in computational methods for healthcare and technical expertise.

GitHub: [@iamdatagabs](https://github.com/iamdatagabs)

## License

Apache-2.0.
