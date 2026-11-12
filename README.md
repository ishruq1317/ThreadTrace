# ThreadTrace — Garment Supply-Chain Traceability & Digital Product Passports

Spring Boot 4.1.2 (Java 21) implementation of **Project 4: ThreadTrace** for CSE 444.

## Quick Start

### 1. Run Full Test Suite (Unit, ArchUnit, E2E Integration)
```bash
./mvnw test
```
*(On Windows PowerShell: `.\mvnw.cmd test`. Note: `PostgresTestcontainersIntegrationTest` runs automatically when Docker is running and skips cleanly otherwise.)*

### 2. Run Full Stack via Docker Compose (C1)
```bash
cp .env.example .env
docker compose up --build
```
Services started (configured within 8 GB RAM budget):
- `threadtrace` (`http://localhost:8080`, Swagger UI at `/swagger-ui.html`, Health at `/actuator/health`)
- `postgres` (`postgres:17-alpine`, port `5432`)
- `minio` (`minio/minio`, API port `9000`, Console port `9001`)

### 3. Run QA Verification & Load Scripts (C5)
```bash
python scripts/qa/run_qa_checks.py --base-url http://localhost:8080
k6 run scripts/qa/qa1_trace_load.js
k6 run scripts/qa/qa4_passport_load.js
```

## Documentation
- Architecture, C4 Diagrams & Mini-ATAM: [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
- ADRs: [docs/architecture/adr/](docs/architecture/adr/)
- Design Pattern Table: [PATTERNS.md](docs/architecture/PATTERNS.md)
- Evidence Report: [EVIDENCE_REPORT.md](docs/evidence/EVIDENCE_REPORT.md)
- AI Usage Log: [AI_USAGE_LOG.md](docs/process/AI_USAGE_LOG.md)
