# Multi-cloud architecture

**Purpose/scope:** comparison and portability seams, not multi-cloud deployment. AWS is proposed primary; Azure/GCP are evaluated. See cloud service mapping/portability strategy and ADR-007.

```mermaid
flowchart TB
  App[Domain modules] --> Ports[Small application-owned ports]
  Ports --> Aws[AWS adapters]
  Ports --> Azure[Azure adapters]
  Ports --> Gcp[GCP adapters]
  Aws --> S3[S3 / SQS / Secrets Manager]
  Azure --> Blob[Blob / Service Bus / Key Vault]
  Gcp --> GCS[GCS / Pub/Sub / Secret Manager]
  App -. common OTel .-> Telemetry[Provider telemetry backend]
```

Abstract object storage, message publishing, secret lookup, and coarse health where consumers truly need equivalent semantics. Preserve provider-specific capabilities (IAM, lifecycle policies, dead lettering, regional replication) in adapters/config, not a lowest-common-denominator framework. Test contracts with emulators/contract suites and provider implementations independently. Risks: differing delivery guarantees, IAM models, consistency, egress, regions, and billing undermine presumed portability. No adapters are implemented yet.
