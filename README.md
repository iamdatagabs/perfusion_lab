# PerfusionLab

**Distributed Biomedical Computing Platform for Cardiovascular Surgery & Extracorporeal Circulation Research**

PerfusionLab is an academic software engineering project exploring computational simulation, visualization and reproducible analysis of simplified cardiovascular and cardiopulmonary bypass (CPB/CEC) scenarios.

> **Educational/research prototype only.** It is not a medical device, clinical decision-support system, or substitute for perfusionist/clinical judgment. Its equations and generated values are simplified demonstrations and must not be used to guide patient care.

## MVP

- Animated, responsive landing page at the repository root.
- Java 21 + Spring Boot API.
- A deterministic, explicitly simplified hemodynamic/oxygen-delivery calculation preview.
- Event publication seam and RabbitMQ-ready infrastructure.
- Containerized local development with PostgreSQL and RabbitMQ.
- Architecture designed for DDD bounded contexts and future distributed simulation workers.

## Architecture direction

The project follows a **hybrid architecture**:

- **DDD:** bounded contexts for Simulation, Perfusion, Physiology, Experiment and Reporting.
- **Microservices where justified:** independent deployment boundaries for API/control plane, simulation workers and analytics as workload and operational needs grow.
- **EDA:** domain events and asynchronous job dispatch; consumers must be idempotent.
- **Grid computing:** simulation jobs are designed to become independent, versioned workloads that can be scheduled across a pool of worker nodes.

The MVP deliberately starts with a small deployable Java application rather than premature service proliferation. The worker contract and event boundary are established first; broker-backed orchestration and multi-node execution are subsequent increments.

## Repository layout

```text
.
├── index.html                 # GitHub Pages landing page + interactive demo
├── backend/                   # Java 21 / Spring Boot API
├── infra/docker-compose.yml   # PostgreSQL + RabbitMQ for local development
└── docs/ARCHITECTURE.md       # Architecture decisions and scale-out roadmap
```

## Run locally

Requirements: Java 21+, Maven 3.9+, Docker Compose.

```bash
docker compose -f infra/docker-compose.yml up -d
cd backend
mvn spring-boot:run
```

API:
- `GET /api/v1/health`
- `POST /api/v1/simulations/preview`

Example request:

```json
{
  "pumpFlowLMin": 4.8,
  "hemoglobinGdL": 10.0,
  "arterialSaturation": 0.98,
  "systemicVascularResistance": 1200.0,
  "bodySurfaceAreaM2": 1.9,
  "temperatureC": 35.5
}
```

The preview returns illustrative calculations including estimated mean arterial pressure and oxygen delivery/index. Values are mathematical demonstrations, not clinical targets or recommendations.

## GitHub Pages

The landing page is a standalone `index.html`. Enable **Settings → Pages → Deploy from a branch → feat/mvp-platform → / (root)** for a preview, or merge the branch into the default branch and publish from `main`.

## Author

**Gabriel Passos de Jesus** — geographer, computer scientist and university lecturer, with a master's degree in Computer Science and Engineering from UEPG and ongoing professional master's studies in Medicine at UEM. Works across software engineering, data science, geotechnologies and digital health, with interests in computational methods for healthcare and technical expertise.

GitHub: [@iamdatagabs](https://github.com/iamdatagabs)

## License

Apache-2.0.