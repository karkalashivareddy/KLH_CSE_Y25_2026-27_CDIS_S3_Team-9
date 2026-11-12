# ADR-014: BCrypt password hashing

- **Status:** Accepted for Phase 2
- **Date:** 2026-11-12
- **Decision:** use Spring Security's maintained BCrypt password encoder with work factor 12. Persist only the encoded hash; response DTOs never contain it. Registration accepts 12–72 characters and separately limits the UTF-8 input to BCrypt's 72-byte maximum to prevent silent truncation. Login errors are generic.
- **Trade-offs:** BCrypt is widely supported but CPU-expensive and has a 72-byte input limit. Benchmark the cost factor on deployment hardware and consider Argon2id with a supported provider when operations can support it.
- **Validation:** service tests verify encoded/non-plaintext storage; PostgreSQL integration tests verify matching and response non-disclosure. No production password is configured.
