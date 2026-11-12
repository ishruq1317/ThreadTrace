package com.threadtrace.compliance;

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
@Table(name = "compliance_alerts")
public class ComplianceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, length = 64)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AlertSeverity severity;

    @Column(name = "brand_code", length = 64)
    private String brandCode;

    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "certificate_id")
    private Long certificateId;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean resolved;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ComplianceAlert() {
    }

    public ComplianceAlert(
            AlertType alertType,
            AlertSeverity severity,
            String brandCode,
            Long lotId,
            Long certificateId,
            String message) {
        this.alertType = alertType;
        this.severity = severity;
        this.brandCode = brandCode;
        this.lotId = lotId;
        this.certificateId = certificateId;
        this.message = message;
        this.resolved = false;
        this.createdAt = Instant.now();
    }

    public void resolve() {
        this.resolved = true;
    }

    public Long getId() {
        return id;
    }

    public AlertType getAlertType() {
        return alertType;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public Long getLotId() {
        return lotId;
    }

    public Long getCertificateId() {
        return certificateId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isResolved() {
        return resolved;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
