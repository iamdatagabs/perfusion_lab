#!/usr/bin/env python3
"""Submit a PerfusionLab experiment batch and report cluster execution metrics."""
import argparse
import json
import time
import urllib.error
import urllib.request


def request_json(url, method="GET", payload=None):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method=method,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as response:
        body = response.read()
        return json.loads(body) if body else {}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--profile", choices=["AUTO", "GENERAL", "MEMORY", "COMPUTE"], default="GENERAL")
    parser.add_argument("--jobs", type=int, default=100)
    parser.add_argument("--parallelism", type=int, default=4)
    parser.add_argument("--baseline-sequential-ms", type=int)
    parser.add_argument("--timeout", type=int, default=300)
    args = parser.parse_args()

    if not 1 <= args.jobs <= 1000:
        parser.error("--jobs must be between 1 and 1000")
    if not 1 <= args.parallelism <= 256:
        parser.error("--parallelism must be between 1 and 256")

    sample = {
        "pumpFlowLMin": 4.8,
        "hemoglobinGdL": 10.0,
        "arterialSaturation": 0.98,
        "systemicVascularResistance": 1200.0,
        "bodySurfaceAreaM2": 1.9,
        "temperatureC": 35.5,
    }
    payload = {
        "simulations": [sample for _ in range(args.jobs)],
        "executionProfile": args.profile,
        "parallelism": args.parallelism,
    }
    if args.baseline_sequential_ms is not None:
        payload["baselineSequentialMs"] = args.baseline_sequential_ms

    base = args.base_url.rstrip("/")
    started = time.monotonic()
    batch = request_json(base + "/api/v1/experiments/batches", "POST", payload)
    batch_id = batch["batchId"]
    deadline = started + args.timeout

    while time.monotonic() < deadline:
        current = request_json(base + "/api/v1/experiments/batches/" + batch_id)
        if current["state"] in ("SUCCEEDED", "PARTIAL", "FAILED"):
            elapsed = time.monotonic() - started
            print(json.dumps({
                "batchId": batch_id,
                "requestedProfile": args.profile,
                "resolvedProfile": current.get("executionProfile"),
                "schedulingReason": current.get("schedulingReason"),
                "jobs": args.jobs,
                "parallelism": args.parallelism,
                "state": current["state"],
                "succeeded": current["succeeded"],
                "failed": current["failed"],
                "wallClockSeconds": round(elapsed, 3),
                "throughputJobsPerSecond": current.get("throughputJobsPerSecond"),
                "speedup": current.get("speedup"),
                "parallelEfficiency": current.get("parallelEfficiency"),
            }, indent=2))
            return 0 if current["failed"] == 0 else 2
        time.sleep(1)

    print(json.dumps({"batchId": batch_id, "state": "TIMEOUT", "timeoutSeconds": args.timeout}))
    return 3


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except urllib.error.HTTPError as exc:
        print(f"HTTP error {exc.code}: {exc.read().decode('utf-8', errors='replace')}")
        raise SystemExit(1)
    except urllib.error.URLError as exc:
        print(f"Connection error: {exc.reason}")
        raise SystemExit(1)
