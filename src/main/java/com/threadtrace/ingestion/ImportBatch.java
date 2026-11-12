package com.threadtrace.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "import_batches")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 80)
    private String batchCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "import_type", nullable = false, length = 40)
    private ImportType importType;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_format", nullable = false, length = 20)
    private ImportFileFormat fileFormat;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected ImportBatch() {
    }

    public ImportBatch(
            String batchCode,
            ImportType importType,
            ImportFileFormat fileFormat,
            String filename,
            int totalRows,
            String uploadedBy) {
        this.batchCode = batchCode;
        this.importType = importType;
        this.fileFormat = fileFormat;
        this.filename = filename;
        this.totalRows = totalRows;
        this.status = "RUNNING";
        this.uploadedBy = uploadedBy;
        this.createdAt = Instant.now();
    }

    public void markCompleted(int acceptedRows, int rejectedRows, String status) {
        this.acceptedRows = acceptedRows;
        this.rejectedRows = rejectedRows;
        this.status = status;
        this.completedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public ImportType getImportType() {
        return importType;
    }

    public ImportFileFormat getFileFormat() {
        return fileFormat;
    }

    public String getFilename() {
        return filename;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getAcceptedRows() {
        return acceptedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public String getStatus() {
        return status;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
