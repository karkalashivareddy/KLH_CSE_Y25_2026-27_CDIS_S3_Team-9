# System architecture

**Purpose/scope:** architectural baseline for the proposed commerce and resilience system. **Status:** target design; only the Phase 1 UI/API shell is being implemented. No cloud deployment exists. Related: [overview](ARCHITECTURE_OVERVIEW.md), ADRs, [security](../05-security/SECURITY_ARCHITECTURE.md), requirements.

## Context and containers

```mermaid
flowchart LR
  Shopper[Shopper] -->|HTTPS| Web[React web app]
  Admin[Administrator / operator] -->|HTTPS + MFA| Web
  Web -->|JSON REST| API[Spring Boot modular monolith]
  API --> DB[(Private PostgreSQL)]
  API --> Cache[(Redis cache)]
  API --> Queue[Queue / event adapter]
  Queue --> Worker[Optional background workers]
  API --> Telemetry[OpenTelemetry collector / backend]
  Operator[Cloud operator] -->|approved control plane| Cloud[Cloud provider APIs]
```

Implemented in Phase 1: local web shell; API health/info. Foundation: docs, local DB/cache Compose services, CI. Future: commerce modules, auth, workers, queue, cloud, telemetry and DR. Optional: Kubernetes only if deployment needs justify it.

## Module boundaries

```mermaid
flowchart TB
  API[API boundary / validation]
  subgraph Monolith[Spring modular monolith]
    Identity[Identity and roles]
    Catalog[Catalog and inventory]
    Cart[Cart]
    Order[Order orchestration]
    Payment[Payment simulation]
    Admin[Administration]
    Recovery[Recovery / incident domain]
  end
  Ports[Infrastructure ports]
  DB[(PostgreSQL)]
  Cache[(Redis)]
  Bus[Queue adapter]
  API --> Identity & Catalog & Cart & Order & Payment & Admin & Recovery
  Identity & Catalog & Cart & Order & Payment & Admin & Recovery --> Ports
  Ports --> DB & Cache & Bus
```

Modules own behavior and data access; avoid direct cross-module table writes. Extract workers only for separately scaled/retried asynchronous tasks with a defined queue contract. Do not introduce distributed transactions for the initial order flow.

## Request, order, and async flows

```mermaid
sequenceDiagram
  actor Browser
  participant Edge as HTTPS edge
  participant API as API boundary
  participant Module as Domain module
  participant Data as Private data adapter
  Browser->>Edge: HTTPS request
  Edge->>API: Forward after TLS / request limits
  API->>API: authenticate, authorize, validate, correlate
  API->>Module: invoke one use case
  Module->>Data: repository / provider port
  Data-->>Module: result
  Module-->>API: domain outcome
  API-->>Browser: stable status + safe response
```

```mermaid
sequenceDiagram
  actor Shopper
  participant UI as React UI
  participant API as Spring API
  participant DB as PostgreSQL
  Shopper->>UI: Submit cart / confirm simulated order
  UI->>API: POST order + idempotency key
  API->>API: authenticate, authorize, validate
  API->>DB: transaction: verify stock, snapshot order, reserve stock
  DB-->>API: committed order
  API-->>UI: 201 order reference
```

Future asynchronous event lifecycle (not implemented):

```mermaid
sequenceDiagram
  participant Order as Order module
  participant Outbox as Transactional outbox
  participant Queue as Provider queue
  participant Worker as Notification worker
  participant DLQ as Dead-letter queue
  Order->>Outbox: persist event with order commit
  Outbox->>Queue: publish with event id
  Queue->>Worker: at-least-once delivery
  Worker->>Worker: idempotency check + handle
  Worker-->>Queue: acknowledge success
  Worker-->>DLQ: retry exhausted / poison message
```

**Security trust boundaries:** public browser/edge → authenticated API → private data plane; CI control plane → OIDC-scoped deployment identity (future); operator → audited recovery control plane. See [network architecture](NETWORK_ARCHITECTURE.md).

## Quality attributes, risks, validation

Stateless API supports horizontal scaling; PostgreSQL remains transactional source of truth; Redis is disposable cache, never sole durable data. Risks include modular boundary erosion, event duplication, cloud lock-in, and unvalidated RTO. Validate modules with tests, integration with compose/Testcontainers, and recovery through timed restore drills. Proposed latency and SLO targets are in NFRs, not measured results.
