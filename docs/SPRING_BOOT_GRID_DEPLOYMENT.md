# Spring Boot + Grid deployment

## Architecture

The lightweight Node service under `preview-api/` exposes only stateless preview calculations. It does not run the job lifecycle, PostgreSQL persistence, RabbitMQ dispatch, worker registry or time-series scenario endpoints.

The full MVP console targets the Spring Boot API. The API must be deployed separately from the GitHub Pages frontend; the Node preview service is not a substitute for it.

| Component | MVP responsibility |
|---|---|
| GitHub Pages | Static frontend |
| Spring Boot API | REST endpoints, Grid registry and timeline generation |
| Neon PostgreSQL | Persistent job, outbox, batch and worker-registry state |
| RabbitMQ | Asynchronous message broker; can be external or locally hosted |
| Worker process | Consumes simulation jobs; MVP may begin with one worker profile |

## Neon PostgreSQL connection

The backend already supports an externally supplied JDBC URL through `DATABASE_URL`. For Neon:

1. In the Neon project dashboard, open **Connect** and select the database and branch intended for the MVP.
2. Prefer the pooled connection endpoint for an application deployment with short-lived/serverless hosting. Neon identifies pooled endpoints by `-pooler` in the hostname.
3. Copy the connection details privately. Do not commit them to GitHub or paste passwords into issues or pull requests.
4. Configure these environment variables on the Spring Boot API and on every worker that accesses the database:

| Variable | Value |
|---|---|
| `DATABASE_URL` | JDBC URL in the form `jdbc:postgresql://HOST/DB?sslmode=require` |
| `DB_USER` | Neon role/user |
| `DB_PASSWORD` | Password for that role |
| `DB_POOL_SIZE` | `3` for a small MVP instance; tune only after measuring |

Use the hostname, database name, username and password shown by Neon. If Neon provides a URI such as `postgresql://USER:PASSWORD@HOST/DB?sslmode=require`, build `DATABASE_URL` as `jdbc:postgresql://HOST/DB?sslmode=require`: change the scheme to `jdbc:postgresql://` and remove the `USER:PASSWORD@` portion from the authority. Put the username and password only in `DB_USER` and `DB_PASSWORD`. Preserve the hostname (including `-pooler`, if selected), database name and SSL query parameter.

The application runs Flyway migrations at startup. A successful API startup and a healthy `/api/v1/health` response are the first connection checks. Do not point production and development deployments at the same Neon branch unless shared data is intentional.

For local development, omit `DATABASE_URL` and the existing local defaults continue to use the Docker Compose PostgreSQL service.

## Render deployment note

The file `render.grid.yaml` is a separate, paid full-stack Render topology template. It provisions its own Render PostgreSQL, RabbitMQ and dedicated workers; it is not the zero-cost Neon MVP topology. Do not provision that Blueprint if the intention is to use Neon and a free-tier deployment.

If deploying only the API to Render, set `DATABASE_URL`, `DB_USER` and `DB_PASSWORD` as private environment variables on the API service. Set the same database variables on any separately deployed worker. Do not create a second Render PostgreSQL database.

## Local run

For a local integration environment:

```bash
docker compose -f infra/docker-compose.yml up --build -d
```

The local stack starts the Spring API, PostgreSQL, RabbitMQ and worker pools. Open `http://localhost:8080`.

## Smoke checks

Health and capabilities:

```bash
curl -fsS http://localhost:8080/api/v1/health
curl -fsS http://localhost:8080/api/v1/cluster/nodes
curl -fsS http://localhost:8080/api/v1/cluster/capacity
```

Generate a univariate temporal series:

```bash
curl -fsS -X POST http://localhost:8080/api/v1/simulations/timeline \
  -H 'Content-Type: application/json' \
  -d '{
    "baseline": {
      "pumpFlowLMin": 4.0,
      "hemoglobinGdL": 10.0,
      "arterialSaturation": 0.98,
      "systemicVascularResistance": 1200.0,
      "bodySurfaceAreaM2": 1.8,
      "temperatureC": 35.0
    },
    "parameter": "pumpFlowLMin",
    "endValue": 6.0,
    "durationSeconds": 60,
    "stepSeconds": 15
  }'
```

The response must contain five points at 0, 15, 30, 45 and 60 seconds, with the input and model outputs at every point. The UI plots and tabulates the returned `points`; it does not synthesize the time series in the browser.

Generate a multivariate temporal series:

```bash
curl -fsS -X POST http://localhost:8080/api/v1/simulations/timeline/multivariate \
  -H 'Content-Type: application/json' \
  -d '{
    "baseline": {
      "pumpFlowLMin": 4.0,
      "hemoglobinGdL": 10.0,
      "arterialSaturation": 0.98,
      "systemicVascularResistance": 1200.0,
      "bodySurfaceAreaM2": 1.8,
      "temperatureC": 35.0
    },
    "ramps": [
      {"parameter": "pumpFlowLMin", "endValue": 6.0},
      {"parameter": "hemoglobinGdL", "endValue": 12.0}
    ],
    "durationSeconds": 60,
    "stepSeconds": 15
  }'
```

These are didactic time-indexed evaluations of a static algebraic model, not physiological dynamic responses. No output is validated for clinical use.

## Frontend connection

- Local Docker Compose: leave the API base URL empty; the browser uses the same origin.
- GitHub Pages: configure the deployed Spring Boot API base URL in **Configurações** in the console.
- The Node preview service is not a substitute for the Spring Boot API. It must not be selected when Grid or timeline generation is required.
