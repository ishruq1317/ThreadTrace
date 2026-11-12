package com.threadtrace.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "import_rejected_rows")
public class ImportRejectedRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_id", nullable = false)
    private Long batchId;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ImportRejectedRow() {
    }

    public ImportRejectedRow(Long batchId, int rowNumber, String rawPayload, String reason) {
        this.batchId = batchId;
        this.rowNumber = rowNumber;
        this.rawPayload = rawPayload;
        this.reason = reason != null && reason.length() > 500 ? reason.substring(0, 500) : (reason != null ? reason : "Invalid row");
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getBatchId() {
        return batchId;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
