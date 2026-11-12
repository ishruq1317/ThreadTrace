package com.threadtrace.parties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "production_lines")
public class ProductionLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_code", nullable = false, unique = true, length = 64)
    private String lineCode;

    @Column(nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factory_id", nullable = false)
    private Party factory;

    @Column(name = "process_type", nullable = false, length = 64)
    private String processType;

    @Column(name = "location_detail", length = 255)
    private String locationDetail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductionLine() {
    }

    public ProductionLine(String lineCode, String name, Party factory, String processType, String locationDetail) {
        this.lineCode = lineCode;
        this.name = name;
        this.factory = factory;
        this.processType = processType;
        this.locationDetail = locationDetail;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getLineCode() {
        return lineCode;
    }

    public String getName() {
        return name;
    }

    public Party getFactory() {
        return factory;
    }

    public String getProcessType() {
        return processType;
    }

    public String getLocationDetail() {
        return locationDetail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
