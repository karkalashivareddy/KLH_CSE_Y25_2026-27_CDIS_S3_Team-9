# ADR-016: Relational catalog, flat categories, and USD-only pricing

- **Status:** Accepted for Phase 3
- **Date:** 2026-11-12
- **Context:** product browsing needs relational integrity, stable SKU/slug URLs, safe lifecycle management, database pagination, and an explicit price policy.
- **Decision:** persist `categories` and `products` in PostgreSQL. Categories are flat in this milestone. Product status is DRAFT/ACTIVE/ARCHIVED; public reads expose ACTIVE only. Archive instead of deleting a product. Enforce unique normalized SKU and slug. Use `NUMERIC(12,2)`/Java `BigDecimal` and support USD only; do not implement FX or accept arbitrary currencies.
- **Search and pagination:** use a parameterized case-insensitive substring search on name/SKU, optional category filter, database `Pageable`, maximum page size 100, and an explicit sort allowlist with ID tie-breaker. No external search/index service or cache.
- **Image URL policy:** store optional HTTPS URLs only (host required, no user-info); the server does not fetch the URL. Render as an image URL, not HTML.
- **Trade-offs:** flat categories and USD-only prices limit merchandising/localization, while avoiding recursive category queries and ambiguous price conversion before product requirements exist.
- **Validation:** PostgreSQL migration constraints, repository pagination/search tests, authorization tests and live API-backed frontend verification.
