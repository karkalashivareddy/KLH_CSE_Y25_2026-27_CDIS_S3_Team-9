# ADR-022: Redis failure is fail-closed and not ready

- **Status:** Accepted for Phase 4
- **Context:** Silently switching to local in-memory sessions would make authentication replica-dependent and violate the distributed-session contract.
- **Decision:** Never fall back from Redis. Redis health is included in the readiness group. If shared-session operations fail, authentication-backed requests return a generic 503 where the request boundary can safely handle the connection failure. No Redis endpoint, credentials, session identifier or stack trace is included in the client error.
- **Existing sessions:** During an outage, requests requiring session lookup cannot authenticate and must not be treated as anonymous success. Readiness fails until Redis is usable. After recovery, sessions still present and unexpired in Redis may be used; if Redis data was lost, users must authenticate again.
- **Scope:** Local Compose Redis is one node with a local persistent AOF volume for development continuity only. This is not Redis HA, replication, backup, or failover.
- **Operations:** Diagnose Redis health/connectivity from service logs and readiness, recover Redis, then allow clients to retry. Do not log session IDs or passwords.
