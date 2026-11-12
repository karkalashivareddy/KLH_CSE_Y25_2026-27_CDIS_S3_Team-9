# ADR-015: Minimal CUSTOMER/ADMIN role-based authorization

- **Status:** Accepted for Phase 2
- **Date:** 2026-11-12
- **Decision:** persist `CUSTOMER` and `ADMIN` roles with an explicit user-role join. Registration assigns CUSTOMER server-side and accepts no role field. Spring Security protects identity and admin endpoints on the backend; frontend route checks are only user experience.
- **Provisioning:** no default administrator is created. This milestone has no production admin provisioning endpoint or bootstrap credential. Initial trusted ADMIN grants require an explicitly reviewed operational/database procedure with identity verification and audit evidence; design that procedure before production use. Tests create an ADMIN fixture directly in an isolated ephemeral database.
- **Trade-offs:** role changes are reflected when a user signs in; existing in-memory sessions keep their authenticated authority snapshot until logout/expiry. Add explicit session revocation when account administration is built.
- **Validation:** integration tests assert customer receives only CUSTOMER, customer is denied `/api/v1/admin/**`, and an isolated ADMIN fixture succeeds.
