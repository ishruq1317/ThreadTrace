# ADR 001: PostgreSQL with Recursive CTEs for Supply-Chain Lineage

## Context
ThreadTrace must trace garment lots backward to every source lot/supplier and forward to every carton/shipment across 6+ process levels with splits and merges (FR2, FR3), returning within 2 seconds on 200,000 lots and 2 million events (QA1), while fitting inside 8 GB RAM in a single Docker Compose command (C1).

## Options Considered
1. **PostgreSQL + `WITH RECURSIVE` CTEs over indexed `lot_edges`** (Spring Data JPA for writes + `JdbcTemplate` for recursive trace queries).
2. **Neo4j via Spring Data Neo4j** alongside PostgreSQL for transactional/Batch data.
3. **JPA iterative BFS/DFS traversal in Java** (loading edges level by level).

## Decision
Use **PostgreSQL with `WITH RECURSIVE` SQL CTEs** over `lot_edges` indexed on `(parent_lot_id, child_lot_id)` and `(child_lot_id, parent_lot_id)`, combined with batched `findByIdIn` hydration when building the `LineageNode` Composite.

## Consequences
- **Positive**: Single relational database fits comfortably in 512 MB container memory on an 8 GB laptop; ACID transactions bind `lot_events`, `lot_edges`, `compliance_alerts`, and Spring Batch job tables together; single SQL round-trip resolves multi-level paths.
- **Negative**: Requires explicit cycle/depth bounds (`depth < maxDepth`) and careful composite indexing on `lot_edges`.
- **Measured Evidence**: Recursive backward/forward trace queries executed cleanly on both H2 PostgreSQL-mode and PostgreSQL SQL grammar in `ThreadTraceEndToEndIntegrationTest` (and `PostgresTestcontainersIntegrationTest` when Docker is active). Full 200k-lot/2M-event benchmark script (`scripts/qa/qa1_trace_load.js`) is provided for load execution against Dockerized PostgreSQL.
