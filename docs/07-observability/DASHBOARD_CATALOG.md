# Dashboard catalog

Eight Grafana dashboards are provisioned from `observability/grafana/dashboards/` and use actual Prometheus metrics. Prometheus, Loki, and Tempo data sources are provisioned from `observability/grafana/provisioning/datasources/datasources.yml`. The dashboards are local operational views; panels show gaps when their underlying workload has not occurred.

| Dashboard | What it shows | Main sources |
|---|---|---|
| Application health and HTTP | Request rate by method/status, p95 latency, 5xx ratio | `http_server_requests_seconds_*` |
| JVM runtime | Heap, non-heap, CPU, garbage collection, and thread behavior | JVM/system Micrometer metrics |
| PostgreSQL pool | Active, idle, and maximum Hikari connections | `hikaricp_connections_*` |
| Redis session store | Redis command rate, mean command duration, and command errors | `lettuce_seconds_*` |
| RabbitMQ processing | Publish/consume rate, connection/channel counts, publish/unroutable failures, and consumer retries | `rabbitmq_*`, `cloudresilience_event_consumer_retries_total` |
| Outbox and event processing | Outbox state counts, oldest pending age, DLQ depth | `cloudresilience_outbox_*`, `cloudresilience_event_dlq_messages` |
| Checkout and orders | Checkout success/failure and order state transitions | `cloudresilience_checkout_*`, `cloudresilience_order_transitions_*` |
| Errors and latency | HTTP server error and latency trends for triage | `http_server_requests_seconds_*` |

## Access and retention

Grafana and Prometheus bind to loopback on the host by default. Grafana anonymous access and user sign-up are disabled; the local environment password is a placeholder and must be replaced before sharing the machine. Prometheus retains seven days of metrics, capped at 2 GB. Loki is configured for seven-day retention. Tempo local blocks are configured with 24-hour retention. These are demo defaults, not a backup or durable operational archive.

The Prometheus scrape endpoint is served on API management port 8081, available only over the internal observability network in Compose. It is not published to the host. The public application listener does not serve the Prometheus endpoint.

Dashboard files contain no seeded, generated, or fabricated measurements. A panel with no real samples may be blank until a request, checkout, or event activity produces data.
