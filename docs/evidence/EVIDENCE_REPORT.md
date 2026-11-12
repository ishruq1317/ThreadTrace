# Quality Attribute & Verification Evidence Report (C9)

## 1. Automated Test Execution (`.\mvnw.cmd -B clean package`)

Executed on Windows 11 with Eclipse Temurin JDK `21.0.13+9-LTS` (`16 tests run, 15 passed, 0 failures, 0 errors, 1 skipped`):
- `ArchitectureRulesTest`: **5 passed, 0 failed** (acyclic module boundaries, layer isolation, controller naming).
- `FootprintAndClaimVisitorTest`: **3 passed, 0 failed** (`BY_MASS` vs `BY_PIECE_COUNT` allocation across splits, expired certificate claim drop, diamond split-merge DAG exactness without double-counting).
- `HashChainIntegrityTest`: **1 passed, 0 failed** (SHA-256 chain validation, broken `previousHash` detection, and environmental-metric tampering detection).
- `LotStateMachineTest`: **3 passed, 0 failed** (quarantine blocking, completed lot rules, flag clearing on correction).
- `MinioS3EvidenceStorageAdapterTest`: **1 passed, 0 failed** (AWS4-HMAC-SHA256 signing, auto bucket creation, PUT/GET round-trip over local HTTP server).
- `PublicPassportWebSliceTest`: **1 passed, 0 failed** (`@WebMvcTest` JSON, Thymeleaf HTML, QR PNG, ETag `304 Not Modified`).
- `ThreadTraceEndToEndIntegrationTest`: **1 passed, 0 failed** (end-to-end FR1–FR10 + QA2–QA5 on H2 PostgreSQL-mode, style backward trace, cross-brand passport/cert isolation, split over-claim detection, paginated passport versions, RFC 9457 401/403/400).
- `PostgresTestcontainersIntegrationTest`: **1 skipped** (Docker Desktop daemon was not running on the host).

### Live Repackaged JAR Verification (`scripts/qa/run_qa_checks.py`)
Booted `target/threadtrace-0.0.1-SNAPSHOT.jar` (`dev` profile) and ran `python scripts/qa/run_qa_checks.py`:
- `[1/5]` `/actuator/health` HTTP 200.
- `[2/5]` 6-level backward trace completed in **83.9 ms** (< 2000 ms).
- `[3/5]` QA2 planted problems (excess cutting loss flagged, orphan lot flagged).
- `[4/5]` QA3 confidentiality & QA4 public passport delivery verified; cross-brand access returned HTTP 403.
- `[5/5]` FR10 hash-chain integrity verified.

## 2. Observed Failed Runs & Fixes Applied

| Failure Run | Symptom | Root Cause | Code Fix |
|---|---|---|---|
| Run 1 (`ThreadTraceEndToEndIntegrationTest`) | Second authenticated HTTP Basic request returned `401 Unauthorized` | `BrandScopedUserDetails` extends Spring Security `User` (`CredentialsContainer`); `ProviderManager` called `eraseCredentials()` after Request 1, mutating the cached instance's password to `null`. | `SecurityConfig.userDetailsService(...)` now returns a fresh `BrandScopedUserDetails` copy per lookup. |
| Run 2 (`ThreadTraceEndToEndIntegrationTest`) | `CUT` after 60%/40% `SPLIT` returned `FLAGGED` instead of `IN_PRODUCTION` | `ClaimRollupVisitor.visitLeaf` used edge `allocatedMassKg` instead of `lot.getMassKg()`, causing `visitComposite` to apply the split fraction twice (`0.60 * 0.60 = 0.36`). | Updated `ClaimRollupVisitor` to compute per-lot certified mass from `lot.getMassKg()` and apply edge `shareRatio` once. |
| Run 3 (`PublicPassportWebSliceTest`) | `@WebMvcTest` failed with `NoSuchBeanDefinitionException: CacheManager` | `@EnableCaching` on `@SpringBootApplication` caused `@WebMvcTest` to require a `CacheManager` while disabling cache auto-configuration. | Extracted `@EnableCaching` and `@EnableScheduling` into `CacheAndSchedulingConfig`. |
| Review Pass (Diamond DAG & Cross-Unit Mass Balance) | Split-then-merge diamond DAG inflated water footprint by 20% (`1200 L` vs `1000 L`); direct cross-unit `CUT` stored parent's full `massKg` on `LotEdge`; `LotEvent` hash ignored `water_liters`/`flagged`; cross-brand passport code hijack & lot-cert leak were unchecked. | Edge allocations were memoized on `LineageNode` instead of `ParentEdge`; `LineageService` checked output `req.unit()` instead of `inputLot.getUnit()`; `MassBalanceCheckPort` lacked `InputSlice`/`ClaimTarget`; `LotEvent.canonicalPayload` omitted footprint/flag fields. | Modeled `LotCompositeNode.ParentEdge`, added `InputSlice`/`ClaimTarget` to `MassBalanceCheckPort`, expanded `LotEvent.canonicalPayload`, added style trace & brand scoping on passports/lot-certs, and moved Spring Batch DDL into idempotent Flyway `V2__spring_batch_schema.sql`. |

## 3. Quality Attribute Status Matrix

| QA | Status | Evidence & Notes |
|---|---|---|
| **QA1 (Trace Speed <= 2s on 200k lots / 2M events)** | **Partially Verified (Functional + Scripted)** | Recursive `WITH RECURSIVE` backward/forward CTEs and composite `lot_edges` indexes executed cleanly in E2E tests and `scripts/qa/qa1_trace_load.js` is ready; **not yet benchmarked against a 200k-lot / 2M-event PostgreSQL dataset** because Docker daemon & `k6` were unavailable locally. |
| **QA2 (Claim Correctness)** | **Verified (E2E)** | `ThreadTraceEndToEndIntegrationTest` verified detection of excess cutting loss (25% > 12%), over-claimed organic content (90% > 0%), orphan lot, expiring-soon cert (<= 30 days), and automatic claim drop (`v2 CLAIMS_DOWNGRADED`) upon certificate expiry. |
| **QA3 (Confidentiality)** | **Verified (E2E + Slice)** | `PublicPassportWebSliceTest` and `ThreadTraceEndToEndIntegrationTest` verified zero confidential fields (`exactAddress`, `workerCount`, `unitPriceUsd`) on `/api/v1/public/passports/{code}` and `/p/{code}`, and `403 ProblemDetail` on cross-brand access. |
| **QA4 (Passport Delivery & DB-down cache)** | **Verified (E2E + Slice, load script ready)** | `ETag`/`304`, `Cache-Control`, Thymeleaf HTML rendering, and simulated `DataAccessResourceFailureException` fallback cache (`X-ThreadTrace-Cache: FALLBACK` HTTP 200) verified in `ThreadTraceEndToEndIntegrationTest`. Throttled mobile load test `scripts/qa/qa4_passport_load.js` awaits `k6` + Docker run. |
| **QA5 (Config-driven adaptability)** | **Verified (E2E)** | `ThreadTraceEndToEndIntegrationTest` configured `CUSTOM_MILL_CERT` and `custom-qa5-field` via `@TestPropertySource` with zero lineage/compliance code changes. |
