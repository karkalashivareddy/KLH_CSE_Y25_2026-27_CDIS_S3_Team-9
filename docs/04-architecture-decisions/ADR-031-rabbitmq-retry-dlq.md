# ADR-031: RabbitMQ topic topology with bounded retry and durable dead-lettering

- **Status:** Accepted for Phase 6
- **Context:** The project needs a free, local-first broker with reliable routing and inspectable failures, without committing to a managed service or requiring broker plugins.
- **Decision:** Use RabbitMQ 4 with a durable topic exchange `cloudresilience.events`, durable consumer queues for notifications and activity, a direct retry exchange with per-consumer TTL queues that dead-letter back to the original queue, and a durable dead-letter exchange with one DLQ per consumer. Messages are persistent, consumers use manual acknowledgement with bounded prefetch, and retry tiers (default 5s/30s/120s) and the maximum of three retries are configuration. The topology is declared in code so it is reviewed with the application.
- **Consequences:** Retries do not block the main queue and preserve message identity, while a poison message eventually lands in a durable DLQ for operator inspection. Broker-level HA, quorum queues, cross-region replication and flow control are not exercised locally. A managed broker can replace RabbitMQ behind the existing publisher/consumer boundary.
- **Rejected alternatives:** Immediate infinite requeue (hot loop); in-memory retry only (lost on crash); Kafka (operational weight); broker plugin delayed-message exchange (extra dependency).
