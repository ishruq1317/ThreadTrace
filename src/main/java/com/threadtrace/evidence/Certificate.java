package com.threadtrace.evidence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "certificates")
public class Certificate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "certificate_number", nullable = false, unique = true, length = 100)
    private String certificateNumber;

    @Column(name = "certificate_type", nullable = false, length = 64)
    private String certificateType;

    @Column(nullable = false, length = 200)
    private String issuer;

    @Column(nullable = false, length = 200)
    private String scope;

    @Column(name = "claimed_attribute", length = 80)
    private String claimedAttribute;

    @Column(name = "certified_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal certifiedPct = BigDecimal.valueOf(100);

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CertificateStatus status;

    @Column(name = "storage_key", nullable = false, length = 255)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 120)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Certificate() {
    }

    public Certificate(
            String certificateNumber,
            String certificateType,
            String issuer,
            String scope,
            String claimedAttribute,
            BigDecimal certifiedPct,
            Long supplierId,
            Long lotId,
            LocalDate validFrom,
            LocalDate validTo,
            CertificateStatus initialStatus,
            String storageKey,
            String originalFilename,
            String contentType,
            long fileSizeBytes) {
        this.certificateNumber = certificateNumber;
        this.certificateType = certificateType;
        this.issuer = issuer;
        this.scope = scope;
        this.claimedAttribute = claimedAttribute;
        this.certifiedPct = certifiedPct != null ? certifiedPct : BigDecimal.valueOf(100);
        this.supplierId = supplierId;
        this.lotId = lotId;
        this.validFrom = validFrom;
        this.validTo = validTo;
        this.status = initialStatus;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.createdAt = Instant.now();
    }

    public CertificateStatus evaluateStatus(LocalDate asOfDate, int warningDays) {
        if (this.status == CertificateStatus.REVOKED) {
            return CertificateStatus.REVOKED;
        }
        if (asOfDate.isAfter(this.validTo) || asOfDate.isBefore(this.validFrom)) {
            if (asOfDate.isAfter(this.validTo)) {
                return CertificateStatus.EXPIRED;
            }
        }
        if (!asOfDate.plusDays(warningDays).isBefore(this.validTo)) {
            return CertificateStatus.EXPIRING_SOON;
        }
        return CertificateStatus.ACTIVE;
    }

    public void updateStatus(CertificateStatus status) {
        this.status = status;
    }

    public void attachStoredFile(String storageKey, String originalFilename, String contentType, long fileSizeBytes) {
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
    }

    public boolean isValidOn(LocalDate asOfDate) {
        if (status == CertificateStatus.REVOKED) {
            return false;
        }
        return !asOfDate.isBefore(validFrom) && !asOfDate.isAfter(validTo);
    }

    public Long getId() {
        return id;
    }

    public String getCertificateNumber() {
        return certificateNumber;
    }

    public String getCertificateType() {
        return certificateType;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getScope() {
        return scope;
    }

    public String getClaimedAttribute() {
        return claimedAttribute;
    }

    public BigDecimal getCertifiedPct() {
        return certifiedPct;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getLotId() {
        return lotId;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidTo() {
        return validTo;
    }

    public CertificateStatus getStatus() {
        return status;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
