# AI Usage & Engineering Verification Log (C11)

## Tools Used
- Antigravity coding agent (architecture scaffolding, Spring Boot 4.1.2 implementation, JUnit 5 / ArchUnit test authoring, documentation).

## Key Prompts
1. Build ThreadTrace (Project 4) strictly against `CSE444_Course_Projects.pdf` (FR1–FR10, QA1–QA5, Spring Boot Architecture Focus, Out of Scope, Common Requirements C1–C11).
2. Keep modular monolith boundaries acyclic (verified with ArchUnit) and preserve incremental git commits.

## Cases Where Agent Output Was Wrong or Incomplete (and How Found & Fixed)

1. **Spring Security `CredentialsContainer.eraseCredentials()` wiped cached in-memory user passwords after the first request**
   - **How found**: Running `ThreadTraceEndToEndIntegrationTest` with Spring Security DEBUG logs showed Request #1 (`operator` creating `MILL-GOTS-1`) succeeded with HTTP 201, while Request #2 (`operator` creating `FAC-1`) failed with `401 Bad credentials`.
   - **Root cause**: `BrandScopedUserDetails` extends `org.springframework.security.core.userdetails.User`, which implements `CredentialsContainer`. `ProviderManager` calls `eraseCredentials()` after authenticating Request #1, mutating the shared `UserDetails` instance stored in `SecurityConfig`'s map (`password = null`).
   - **Fix**: Updated `SecurityConfig.userDetailsService(...)` to return a fresh defensive `new BrandScopedUserDetails(...)` instance on every lookup.

2. **`ClaimRollupVisitor` double-counted split fraction during lineage roll-up**
   - **How found**: `ThreadTraceEndToEndIntegrationTest` failed at the `CUT` step after a 60%/40% `SPLIT` (`LOT-FAB-A1` -> `LOT-FAB-A1-S1`) with `status = FLAGGED` instead of `IN_PRODUCTION`.
   - **Root cause**: `ClaimRollupVisitor.visitLeaf(...)` multiplied `leaf.getAllocatedMassKgFromParent()` (60 kg) by `claimedPct` (80% -> 48 kg), and then `visitComposite(...)` multiplied that result by `shareRatio` (60/100 = 0.60 -> 28.8 kg), squaring the split ratio.
   - **Fix**: Updated `ClaimRollupVisitor` so each node computes certified mass against its own `lot.getMassKg()` and scales by `shareRatio` once at the parent-to-child edge.

3. **Spring Boot 4.1.2 / Spring Batch 6.0.6 package & API changes**
   - **How found**: Inspecting `mvnw dependency:list` and running `javap` against `spring-batch-core:6.0.6` and `spring-batch-infrastructure:6.0.6` revealed Spring Boot 4 uses Jackson 3 (`tools.jackson.databind.ObjectMapper`), `HttpStatus.UNPROCESSABLE_CONTENT`, and Spring Batch 6 moved item interfaces into `org.springframework.batch.infrastructure.item.*` with `StepBuilder.chunk(int).transactionManager(...)`.
   - **Fix**: Aligned `pom.xml`, `ProblemDetailExceptionHandler`, `PassportService`, and `FactoryImportJobConfig` to the verified Spring Boot 4.1.2 / Spring Batch 6.0.6 APIs with zero compilation errors or deprecation warnings.
