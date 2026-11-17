# SLI and SLO definitions

**Status:** initial service objectives for the academic deployment. These are targets, not observed production results.

## Service boundary and assumptions

The service boundary is the Spring Boot API behind the local web container. PostgreSQL owns durable business state. Redis stores shared HTTP sessions. RabbitMQ carries downstream order events; committed orders remain correct if RabbitMQ is temporarily unavailable because the transactional outbox is in PostgreSQL. The local Compose profile is a single-node development environment and cannot demonstrate cloud availability.

The objectives below are proposed starting points for a low-volume demonstration environment. They assume a healthy host, a single local region, and no external payment or notification provider. Revisit them after representative workload and operational evidence exists.

## Objectives and measurement

| SLI | Initial SLO target | Measurement and window | Alert relationship |
|---|---|---|---|
| API availability | At least 99.5% of eligible requests return without a 5xx over a rolling 30 days | `1 - (5xx request rate / all request rate)` from `http_server_requests_seconds_count`; exclude health probes and intentional client errors from a production SLI calculation | `ApiHigh5xxRatio` is a short-window symptom alert, not proof of SLO burn |
| API latency | p95 below 750 ms for eligible API requests over a rolling 30 days | Histogram quantile from `http_server_requests_seconds_bucket`; report endpoint mix and traffic volume alongside the percentile | `ApiHighP95Latency` uses a 10-minute persistence window |
| Checkout success | At least 99% of valid, sufficiently stocked checkout attempts reach a terminal successful simulated-payment outcome, excluding invalid input, insufficient inventory, and client cancellation | `cloudresilience_checkout_attempts_total{outcome=...}`; calculate only from a defined valid-attempt denominator | No checkout-specific alert is configured yet; the metric is available for review |
| Outbox delay | 99% of committed events become published within 60 seconds while the broker is available | `cloudresilience_outbox_oldest_pending_seconds` plus state counts; compare event occurrence and broker confirm times for an auditable sample | `OutboxBacklogOld` alerts when the oldest pending event exceeds five minutes for five minutes |
| Consumer processing | 99% of supported messages complete their consumer effect within 30 seconds, excluding time intentionally spent in retry delay | Consumer duration and retry counters; DLQ count must be reported separately | `EventConsumerRetries` and `EventDeadLetterQueueGrowing` signal persistent failures |
| Dependency visibility | API scrape and dependency readiness remain observable; do not turn optional asynchronous dependencies into application liveness dependencies | Prometheus scrape `up` and application health groups | `ApiScrapeMissing` alerts after two minutes; database, Redis, and broker recovery follow the runbook |

SLO compliance cannot be inferred from the short local catalog test. No 30-day history or representative checkout load exists. The checkout and consumer percentages above remain proposed objectives until a denominator and representative workload are established.

## Metric and label policy

Prometheus scrapes Actuator on the API's internal management port (`8081`). Compose publishes only the application listener (`8080`) to the host; Prometheus and the management listener share the private observability network. Application custom metrics use bounded outcome, status, and consumer labels. Customer identifiers, email addresses, order IDs, session IDs, and message IDs must not be metric labels.

The local load result is recorded in [Load-test results](LOAD_TEST_RESULTS.md). It measures catalog browsing only, in one run on a developer machine. It is not an availability or capacity claim.
