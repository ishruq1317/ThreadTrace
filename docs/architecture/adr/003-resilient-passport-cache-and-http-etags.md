# ADR 003: Resilient Public Passport Caching, ETags, and Audience Proxy

## Context
QA3 requires zero confidential fields on any public endpoint and strict cross-brand isolation. QA4 requires public passport pages to load under 2 seconds on throttled mobile connections and still be served from cache when the database is down.

## Options Considered
1. **External Redis cache + shared DTOs with `@JsonIgnore`**.
2. **Dedicated DTO projections per audience via `AudiencePassportProxy` + HTTP `Cache-Control`/`ETag` + in-process `ResilientPassportCache` fallback on `DataAccessException`**.

## Decision
Use separate Java record DTOs (`PublicPassportView`, `BrandPassportView`, `AuditorPassportView`) enforced by `AudiencePassportProxy` + `AudienceAccessService`, paired with inline-styled Thymeleaf HTML (zero external assets), HTTP `ETag` (`304 Not Modified`), and an in-process Caffeine `ResilientPassportCache` that intercepts `DataAccessException` during DB outages.

## Consequences
- **Positive**: Confidential fields (`exactAddress`, `workerCount`, `unitPriceUsd`) do not exist on `PublicPassportView`, preventing accidental serialization leaks; zero extra Redis container memory needed for 8 GB RAM laptops; DB outage gracefully falls back to `X-ThreadTrace-Cache: FALLBACK` HTTP 200 responses.
- **Negative**: In-process fallback cache is per-instance (acceptable for single-node deployment; multi-replica deployment would pair with Redis).
- **Measured Evidence**: Verified in `ThreadTraceEndToEndIntegrationTest`: public JSON and HTML contain zero confidential fields, `If-None-Match` returns `304`, cross-brand calls return `403` ProblemDetail, and simulated `DataAccessResourceFailureException` returns `200` from fallback cache.
