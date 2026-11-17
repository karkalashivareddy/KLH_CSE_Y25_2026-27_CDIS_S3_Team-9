# Alerting and response runbook

Prometheus loads the version-controlled rules in `observability/prometheus/alerts.yml`. Alerts are local rules and do not currently page an on-call system. Severity `page` means investigate immediately during an attended demonstration; `ticket` means triage and create a follow-up. Thresholds are initial operational signals, not statistically tuned production thresholds.

## Alert response

| Alert | Trigger | First response |
|---|---|---|
| `ApiHigh5xxRatio` | 5xx ratio above 5% for five minutes | Check API readiness and recent structured logs. Follow the trace ID for representative failures. Check PostgreSQL, Redis, and RabbitMQ health groups before restarting anything. |
| `ApiHighP95Latency` | p95 above 750 ms for ten minutes | Inspect request volume and route mix, then Hikari utilization, JVM/GC panels, PostgreSQL health, and event backlog. Compare with the documented workload; a low-traffic local percentile can be noisy. |
| `ApiScrapeMissing` | Prometheus cannot scrape the API for two minutes | Check `docker compose ps`, API readiness at management port 8081 from the observability network, and the Prometheus target's `lastError`. Do not publish the management port to the host as a workaround. |
| `OutboxBacklogOld` | Oldest pending outbox event above five minutes for five minutes | Confirm RabbitMQ connectivity and publisher-confirm behavior. Inspect API logs and pending event age/count. The order transaction is already durable; keep the broker recovery and publication path available. Do not edit outbox rows manually. |
| `EventConsumerRetries` | Retry rate remains positive for five minutes | Identify consumer and bounded error category in logs/metrics. Check downstream database and broker readiness. Allow transient retry tiers to complete before replaying anything. |
| `EventDeadLetterQueueGrowing` | A consumer DLQ remains non-empty for two minutes | Inspect the DLQ and durable quarantine record. Determine whether the payload is malformed, unsupported, or blocked by a dependency. Correct the cause, then use the authenticated administrative replay flow with its required reason; verify inbox deduplication and the consumer effect afterward. |
| `PrometheusStorageNearLocalLimit` | TSDB block storage exceeds 1.8 GB for ten minutes | Check the configured 7-day/2-GB limits, retention, and Docker volume usage. Export any evidence required by the exercise before changing retention. Do not delete volumes as first response. |

## Recovery order

1. Establish whether the issue affects customer API requests, asynchronous event processing, or telemetry only.
2. Inspect Prometheus targets and alert expression values; check Grafana dashboards for correlated HTTP, JVM, database-pool, Redis, RabbitMQ, and outbox signals.
3. Search Loki by service/container and time range. Use the trace ID from a safe log entry to open the corresponding Tempo trace.
4. Restore the failed dependency or process. Preserve PostgreSQL as the business source of truth; a broker or observability outage must not cause a manual order rewrite.
5. Verify API readiness, metrics scraping, and a representative request. For messaging incidents, verify outbox progress, consumer effects, and DLQ state before declaring recovery.
6. Record timestamps, observed impact, trace/log references, and follow-up work in the phase incident notes.

## Local operation

Start the application and telemetry profile with:

```powershell
Copy-Item .env.example .env  # only if .env does not already exist
docker compose --env-file observability.env.example --env-file .env --profile observability up -d --build
```

Prometheus is at `http://127.0.0.1:9090`; Grafana is at `http://127.0.0.1:3000`. Replace the sample Grafana password before using a shared machine. In Grafana, the provisioned Prometheus, Loki, and Tempo data sources are in the CloudResilience folder. Prometheus rules do not send external notifications. Local Compose is not a production alerting service.

To stop the stack while keeping named volumes:

```powershell
docker compose --env-file observability.env.example --env-file .env --profile observability down
```

The local Alloy agent reads Docker logs through the Docker socket. Docker socket access is highly privileged even when mounted read-only. The collector filters for the `cloudresilience` Compose project and should only be enabled on a trusted developer workstation. Do not expose Grafana, Prometheus, Loki, Tempo, or the management listener publicly.
