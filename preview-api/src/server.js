import http from "node:http";
import { evaluatePreview } from "./model.js";

const PORT = Number(process.env.PORT || 8080);
const DEFAULT_ORIGINS = ["https://iamdatagabs.github.io"];
const allowedOrigins = (process.env.CORS_ALLOWED_ORIGINS || DEFAULT_ORIGINS.join(","))
  .split(",").map(value => value.trim()).filter(Boolean);
const MAX_BODY_BYTES = 16 * 1024;

function sendJson(res, statusCode, payload) {
  res.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  res.end(JSON.stringify(payload));
}

async function readJson(req) {
  let body = "";
  for await (const chunk of req) {
    body += chunk;
    if (Buffer.byteLength(body) > MAX_BODY_BYTES) {
      const error = new Error("Request body too large");
      error.statusCode = 413;
      throw error;
    }
  }
  try {
    return JSON.parse(body);
  } catch {
    const error = new Error("Request body must be valid JSON");
    error.statusCode = 400;
    throw error;
  }
}

export function createServer() {
  return http.createServer(async (req, res) => {
    const origin = req.headers.origin;
    if (origin && allowedOrigins.includes(origin)) {
      res.setHeader("access-control-allow-origin", origin);
      res.setHeader("vary", "Origin");
      res.setHeader("access-control-allow-methods", "GET, POST, OPTIONS");
      res.setHeader("access-control-allow-headers", "Content-Type");
    } else if (origin) {
      return sendJson(res, 403, { error: "Origin not allowed" });
    }

    if (req.method === "OPTIONS") {
      res.writeHead(204);
      return res.end();
    }

    const path = new URL(req.url, "http://localhost").pathname;
    if (req.method === "GET" && path === "/api/v1/health") {
      return sendJson(res, 200, {
        status: "UP",
        service: "perfusionlab-preview-api",
        version: "1.0.0"
      });
    }

    if (req.method === "POST" && path === "/api/v1/simulations/preview") {
      try {
        const input = await readJson(req);
        return sendJson(res, 200, evaluatePreview(input));
      } catch (error) {
        return sendJson(res, error.statusCode || 500, {
          error: error.message || "Internal server error",
          ...(error.details ? { details: error.details } : {})
        });
      }
    }

    return sendJson(res, 404, { error: "Route not found" });
  });
}

if (process.argv[1] && new URL(import.meta.url).pathname === process.argv[1]) {
  createServer().listen(PORT, "0.0.0.0", () => {
    console.log(`PerfusionLab preview API listening on port ${PORT}`);
  });
}
