package com.threadtrace.passport;

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
@Table(name = "product_passports")
public class ProductPassport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "passport_code", nullable = false, length = 80)
    private String passportCode;

    @Column(nullable = false)
    private int version;

    @Column(name = "latest_version", nullable = false)
    private boolean latestVersion;

    @Column(name = "style_code", nullable = false, length = 80)
    private String styleCode;

    @Column(name = "style_name", nullable = false, length = 200)
    private String styleName;

    @Column(name = "shipment_lot_id", nullable = false)
    private Long shipmentLotId;

    @Column(name = "brand_code", nullable = false, length = 64)
    private String brandCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PassportStatus status;

    @Column(name = "allocation_method", nullable = false, length = 32)
    private String allocationMethod;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(nullable = false, length = 80)
    private String etag;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductPassport() {
    }

    public ProductPassport(
            String passportCode,
            int version,
            boolean latestVersion,
            String styleCode,
            String styleName,
            Long shipmentLotId,
            String brandCode,
            PassportStatus status,
            String allocationMethod,
            String payloadJson,
            String contentHash,
            String etag) {
        this.passportCode = passportCode;
        this.version = version;
        this.latestVersion = latestVersion;
        this.styleCode = styleCode;
        this.styleName = styleName;
        this.shipmentLotId = shipmentLotId;
        this.brandCode = brandCode;
        this.status = status;
        this.allocationMethod = allocationMethod;
        this.payloadJson = payloadJson;
        this.contentHash = contentHash;
        this.etag = etag;
        this.createdAt = Instant.now();
    }

    public void markSuperseded() {
        this.latestVersion = false;
    }

    public Long getId() {
        return id;
    }

    public String getPassportCode() {
        return passportCode;
    }

    public int getVersion() {
        return version;
    }

    public boolean isLatestVersion() {
        return latestVersion;
    }

    public String getStyleCode() {
        return styleCode;
    }

    public String getStyleName() {
        return styleName;
    }

    public Long getShipmentLotId() {
        return shipmentLotId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public PassportStatus getStatus() {
        return status;
    }

    public String getAllocationMethod() {
        return allocationMethod;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getEtag() {
        return etag;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
