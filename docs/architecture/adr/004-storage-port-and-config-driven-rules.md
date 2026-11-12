# ADR 004: Storage Port Adapter and Configuration-Driven Compliance Rules

## Context
FR5 requires storing certificate/test report files behind a storage port (local disk or MinIO). QA5 requires adding a new certificate type, process step, emission factor, or passport field through configuration alone without editing lineage or mass-balance code.

## Options Considered
1. **Hardcoded `enum`s for certificate types and process steps, plus direct `java.nio.file` calls in `EvidenceService`**.
2. **`EvidenceStoragePort` interface with conditional `LocalDiskEvidenceStorageAdapter` / `MinioS3EvidenceStorageAdapter` + validated `@ConfigurationProperties` (`ThreadTraceProperties`)**.

## Decision
Define `EvidenceStoragePort` with `@ConditionalOnProperty` adapters (`local` and `minio` using AWS SigV4 over `RestClient`), and externalize allowed certificate types, per-process loss limits/emission factors, allocation defaults, and extra passport fields into `@Validated` `ThreadTraceProperties`.

## Consequences
- **Positive**: Switching between local disk and MinIO requires only `THREADTRACE_STORAGE_TYPE=minio`; adding a certificate type (`CUSTOM_MILL_CERT`), process step, emission factor, or passport field requires only `application.yml` / environment properties.
- **Negative**: Dynamic property keys are strings rather than compile-time enums, mitigated by Bean Validation constraints (`@DecimalMin`, `@DecimalMax`, `@NotBlank`) at startup.
- **Measured Evidence**: Verified in `ThreadTraceEndToEndIntegrationTest` via `@TestPropertySource` adding `CUSTOM_MILL_CERT` and `custom-qa5-field` with zero code edits.
