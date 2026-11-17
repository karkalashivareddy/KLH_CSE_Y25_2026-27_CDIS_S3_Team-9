# Load-test results

**Run date:** 2026-11-17  
**Status:** one bounded local catalog-browsing run completed; no production-scale claim.

## Reproduction

With the application stack running, run:

```powershell
docker compose --env-file observability.env.example --env-file .env --profile load-test run --rm k6
```

The version-controlled k6 scenario in `performance/k6/catalog.js` sends `GET /api/v1/products?page=0&size=20` through the local web container. It uses five virtual users for 30 seconds, checks for HTTP 200, and enforces a failure-rate threshold below 1% and p95 below one second.

## Measurement

| Metric | Observed value |
|---|---:|
| Virtual users | 5 |
| Duration | 30 seconds |
| Catalog requests | 150 |
| Request rate | 4.9386 requests/second |
| Failed requests | 0 (0%) |
| p50 latency | 8.6669 ms |
| p95 latency | 21.1790 ms |
| p99 latency | 29.3967 ms |
| Thresholds | PASS |

These values describe this single run on the developer's local Docker environment only. Host CPU/memory utilization, database saturation, queue throughput, and outbox lag were not collected as part of the test. Authentication, cart, checkout, order reads, and event consumer throughput were not load-tested here. Repeat with a recorded hardware profile and broader scenarios before using the numbers to size cloud resources or make an SLO claim.
