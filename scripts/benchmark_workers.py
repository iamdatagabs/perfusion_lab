#!/usr/bin/env python3
"""Small HTTP benchmark for PerfusionLab's asynchronous job API.

Run once per worker-pool size, e.g. after:
  docker compose -f infra/docker-compose.yml up -d --scale worker=4
"""
import argparse
import concurrent.futures
import json
import statistics
import time
import urllib.error
import urllib.request


def request_json(url, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    headers = {"Content-Type": "application/json"} if data else {}
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.loads(response.read().decode("utf-8"))


def percentile(values, p):
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((len(ordered) - 1) * p)))
    return ordered[index]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--jobs", type=int, default=100)
    parser.add_argument("--concurrency", type=int, default=16)
    parser.add_argument("--timeout", type=int, default=180)
    parser.add_argument("--workers", type=int, required=True,
                        help="Worker replica count currently being benchmarked")
    args = parser.parse_args()

    payload = {
        "pumpFlowLMin": 4.8,
        "hemoglobinGdL": 10.0,
        "arterialSaturation": 0.98,
        "systemicVascularResistance": 1200.0,
        "bodySurfaceAreaM2": 1.9,
        "temperatureC": 35.5,
    }
    endpoint = args.base_url.rstrip("/") + "/api/v1/simulations/jobs"
    started = time.perf_counter()

    def submit(_):
        result = request_json(endpoint, "POST", payload)
        return result["jobId"], time.perf_counter()

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        submitted = list(pool.map(submit, range(args.jobs)))

    latencies = []
    terminal = {"SUCCEEDED", "FAILED"}
    deadline = time.monotonic() + args.timeout
    pending = {job_id: submitted_at for job_id, submitted_at in submitted}
    counts = {"SUCCEEDED": 0, "FAILED": 0}

    while pending and time.monotonic() < deadline:
        for job_id, submitted_at in list(pending.items()):
            try:
                state = request_json(endpoint + "/" + job_id)["state"]
            except (urllib.error.URLError, TimeoutError):
                continue
            if state in terminal:
                counts[state] += 1
                latencies.append(time.perf_counter() - submitted_at)
                del pending[job_id]
        if pending:
            time.sleep(0.15)

    elapsed = time.perf_counter() - started
    completed = len(latencies)
    report = {
        "worker_replicas": args.workers,
        "submitted": args.jobs,
        "succeeded": counts["SUCCEEDED"],
        "failed": counts["FAILED"],
        "timed_out_or_pending": len(pending),
        "wall_time_seconds": round(elapsed, 3),
        "throughput_jobs_per_second": round(completed / elapsed, 3) if elapsed else 0,
        "latency_seconds": {
            "p50": round(percentile(latencies, 0.50), 3),
            "p95": round(percentile(latencies, 0.95), 3),
            "mean": round(statistics.mean(latencies), 3) if latencies else None,
        },
    }
    print(json.dumps(report, indent=2))
    if pending or counts["FAILED"]:
        raise SystemExit(2)


if __name__ == "__main__":
    main()
