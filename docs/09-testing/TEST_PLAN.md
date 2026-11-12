# Test plan

**Purpose/scope:** executable foundation test inventory, linked to AC and requirements. Status is pending until command output is recorded.

| Test | Scenario | Expected | Layer |
|---|---|---|---|
| WEB-001 | Overview renders with API unavailable | loading-safe UI, no fake telemetry, disconnected message | Vitest |
| API-001 | GET `/api/v1/health` | 200 and expected status/application fields | Spring MockMvc |
| API-002 | GET `/api/v1/info` | 200 safe metadata; no password property | Spring MockMvc |
| API-003 | unknown API path | standard framework 404; no sensitive stack response | API smoke/security review |
| CFG-001 | external server port | app honors environment value | startup test (future) |
| COMPOSE-001 | parse compose file | valid service/network/volume config | Docker Compose CLI |
| COMPOSE-002 | local startup | health checks pass; DB host port loopback-only | Docker integration, if daemon |
| SEC-001 | source secret scan | no live credentials | gitleaks/scanner |
| CI-001 | workflow syntax and permissions | YAML valid; read-only baseline; no credentials | review/linter |

Known environment constraints at inspection: Maven and Terraform absent; Docker binary exists but daemon/Compose plugin must be tested. Do not mark skipped/blocked cases pass.
