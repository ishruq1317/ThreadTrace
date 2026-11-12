# Design Pattern Justification (C8)

Every pattern below is mapped to specific ThreadTrace requirements, participating classes, and the rejected alternative.

| Pattern | Requirement Served | Participating Classes | Alternative Rejected |
|---|---|---|---|
| **Composite** | FR3, FR4, FR6 (multi-level split/merge lineage graph & roll-ups) | `LineageNode`, `LotLeafNode`, `LotCompositeNode`, `LineageCompositeBuilder` | Flat edge list traversal duplicated across compliance and footprint services (would scatter recursion and cycle guards). |
| **Visitor** | FR4, FR5, FR6 (claim validation and footprint roll-up over lineage without polluting node entities) | `LineageVisitor`, `ClaimRollupVisitor`, `FootprintRollupVisitor` | Embedding compliance and environmental math directly inside `LotLeafNode`/`LotCompositeNode` (violates SRP and makes adding new roll-ups require editing composite nodes). |
| **Strategy** | FR6 (configurable footprint allocation through splits and merges) | `AllocationStrategy`, `MassAllocationStrategy`, `PieceCountAllocationStrategy`, `FootprintService` | Hardcoded `if/else` switch inside `FootprintService` (harder to extend with new allocation rules). |
| **Builder** | FR7, QA5 (extensible Digital Product Passport assembly with configurable extra fields) | `ProductPassportBuilder`, `PassportService` | Telescoping constructors or direct `PassportCanonicalPayload` instantiation (forces lineage/mass-balance callers to break when adding passport fields). |
| **Adapter** | FR5 (storage port), FR9 (CSV and Excel factory import formats) | `EvidenceStoragePort`, `LocalDiskEvidenceStorageAdapter`, `MinioS3EvidenceStorageAdapter`, `ImportRowAdapter`, `CsvImportAdapter`, `ExcelImportAdapter` | Direct `java.nio.file` calls in `EvidenceService` and format-specific batch readers duplicated per file type. |
| **Proxy** | FR8, QA3 (audience-restricted passport views & per-brand isolation) | `AudiencePassportProxy`, `AudienceAccessService` | Returning a single shared entity/DTO with `@JsonIgnore` flags (prone to accidental leaks on new endpoints). |
| **State** | FR2, FR4 (lot lifecycle transitions and quarantine blocking) | `LotState`, `LotState.CreatedState`, `InProductionState`, `CompletedState`, `FlaggedState`, `QuarantinedState`, `Lot` | Scattered `if (lot.getStatus() == ...)` checks across every event handler. |
| **Observer** | FR4, FR5 (expiry alerts and automatic passport claim recalculation) | `LotEventRecordedEvent`, `CertificateEvents`, `ComplianceEventObserver`, `PassportExpiryListener` | Coupling `EvidenceService` directly to `PassportService` and `ComplianceAlertRepository` (creates circular module dependencies and tight coupling). |
