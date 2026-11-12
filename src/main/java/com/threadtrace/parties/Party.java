package com.threadtrace.parties;

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

@Entity
@Table(name = "parties")
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type", nullable = false, length = 40)
    private PartyType partyType;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 150)
    private String region;

    @Column(name = "exact_address", length = 500)
    private String exactAddress;

    @Column(name = "worker_count")
    private Integer workerCount;

    @Column(name = "unit_price_usd", precision = 19, scale = 4)
    private BigDecimal unitPriceUsd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Party() {
    }

    public Party(
            String code,
            String name,
            PartyType partyType,
            String country,
            String region,
            String exactAddress,
            Integer workerCount,
            BigDecimal unitPriceUsd) {
        this.code = code;
        this.name = name;
        this.partyType = partyType;
        this.country = country;
        this.region = region;
        this.exactAddress = exactAddress;
        this.workerCount = workerCount;
        this.unitPriceUsd = unitPriceUsd;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public PartyType getPartyType() {
        return partyType;
    }

    public String getCountry() {
        return country;
    }

    public String getRegion() {
        return region;
    }

    public String getExactAddress() {
        return exactAddress;
    }

    public Integer getWorkerCount() {
        return workerCount;
    }

    public BigDecimal getUnitPriceUsd() {
        return unitPriceUsd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
