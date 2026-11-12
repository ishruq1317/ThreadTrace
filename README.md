# ThreadTrace — Garment Supply-Chain Traceability & Digital Product Passports

Spring Boot 4.1.2 (Java 21) implementation of **Project 4: ThreadTrace** for CSE 444.

[![Live on Render](https://img.shields.io/badge/Render-Live%20Demo-brightgreen?logo=render)](https://threadtrace.onrender.com/swagger-ui.html)
[![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://adoptium.net)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-green?logo=springboot)](https://spring.io)

### Live Cloud Deployment
- **Swagger UI (Interactive API Docs):** [https://threadtrace.onrender.com/swagger-ui.html](https://threadtrace.onrender.com/swagger-ui.html)
- **Actuator Health Probe:** [https://threadtrace.onrender.com/actuator/health](https://threadtrace.onrender.com/actuator/health)

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
# Functional & security verification (QA1-QA5 + FR10) against a running instance
python scripts/qa/run_qa_checks.py --base-url http://localhost:8080

# Seed PostgreSQL with 200,000 lots and 2,000,000 events across 6+ process levels for QA1 benchmarking
# (Start Docker Compose postgres + threadtrace first so Flyway creates the schema)
psql -h localhost -U threadtrace -d threadtrace -f scripts/qa/seed_qa1_dataset.sql
# Or inside the running Compose postgres container:
# docker compose exec -T postgres psql -U threadtrace -d threadtrace < scripts/qa/seed_qa1_dataset.sql

# Run k6 load tests for QA1 (6-level trace <= 2s) and QA4 (public passport <= 2s under throttled mobile load)
k6 run scripts/qa/qa1_trace_load.js
k6 run scripts/qa/qa4_passport_load.js
```

## Documentation
- Architecture, C4 Diagrams & Mini-ATAM: [ARCHITECTURE.md](docs/architecture/ARCHITECTURE.md)
- ADRs: [docs/architecture/adr/](docs/architecture/adr/)
- Design Pattern Table: [PATTERNS.md](docs/architecture/PATTERNS.md)
- Evidence Report: [EVIDENCE_REPORT.md](docs/evidence/EVIDENCE_REPORT.md)
- AI Usage Log: [AI_USAGE_LOG.md](docs/process/AI_USAGE_LOG.md)
