# ADR 002: Immutable Hash-Chained Events with CQRS Read Models

## Context
FR2 requires immutable lot events where corrections are appended as new events; FR10 requires per-lot hash chaining and tamper verification; FR5 and FR7 require versioned passports that automatically drop claims when certificates expire while keeping old passport versions retrievable.

## Options Considered
1. **Immutable `LotEvent` rows + SHA-256 hash chain + versioned `product_passports` CQRS read models** updated via Spring application events.
2. **Mutable lot/event tables** updated in place with audit triggers.
3. **Full Event Sourcing** reconstructing every aggregate on every read without read models.

## Decision
Implement append-only `@Immutable` `LotEvent` records chained per lot (`sequenceNumber`, `previousHash`, `eventHash`) verified by `EventIntegrityService`, alongside versioned `product_passports` read models regenerated asynchronously/synchronously via `@TransactionalEventListener` when supporting certificates expire.

## Consequences
- **Positive**: Any SQL-level modification to historical events breaks the SHA-256 chain and is detected immediately by `GET /api/v1/integrity/passports/{code}/verify`; public passport reads never recompute full lineage graphs on the hot path; historical versions (`v1`, `v2`, ...) remain queryable.
- **Negative**: Requires regenerating a new passport version on certificate expiry (`CLAIMS_DOWNGRADED`).
- **Measured Evidence**: Verified in `ThreadTraceEndToEndIntegrationTest`: `CORRECTION` event increments sequence cleanly, certificate expiry triggers automatic `v2` passport dropping claims while `v1` stays retrievable, and direct SQL `UPDATE` on `lot_events` flips `verified` from `true` to `false`.
