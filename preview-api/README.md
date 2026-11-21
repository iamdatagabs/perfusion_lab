# PerfusionLab Preview API

Minimal stateless HTTP API for the educational simulation preview. It is intentionally independent of PostgreSQL, RabbitMQ, workers and the distributed job pipeline.

## Run locally

Requires Node.js 20 or newer.

```bash
npm test
npm start
```

The server listens on `PORT` (default `8080`).

## Endpoints

- `GET /api/v1/health`
- `POST /api/v1/simulations/preview`

Example request:

```json
{
  "pumpFlowLMin": 4,
  "hemoglobinGdL": 10,
  "arterialSaturation": 0.98,
  "systemicVascularResistance": 1200,
  "bodySurfaceAreaM2": 1.8,
  "temperatureC": 35
}
```

The preview response preserves the field names and educational equations used by the Java API. Invalid input returns HTTP 400 with field-level details. Request bodies are limited to 16 KiB.

## CORS

The default allowed origin is `https://iamdatagabs.github.io`. Override it with a comma-separated `CORS_ALLOWED_ORIGINS` environment variable if the frontend origin changes. Requests without an Origin header (for example, server-to-server or local curl requests) are accepted.

## Render

The repository-root `render.yaml` defines a small Node web service with this directory as its root. No database, broker, secrets or paid external services are required by this API. Configure the frontend API base URL to the deployed service URL.

## Scope and safety

This service implements only a simplified educational calculation. It is not clinically validated, is not a medical device, and must not be used for patient care, perfusion management or treatment decisions. The API does not provide clinical interpretation or recommendations.
