package com.threadtrace.lineage;

import com.threadtrace.shared.HashUtils;
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
@Table(name = "lots")
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_code", nullable = false, unique = true, length = 80)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_type", nullable = false, length = 40)
    private LotType lotType;

    @Column(name = "material_type", nullable = false, length = 100)
    private String materialType;

    @Column(name = "style_code", length = 80)
    private String styleCode;

    @Column(name = "brand_code", nullable = false, length = 64)
    private String brandCode;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "factory_id")
    private Long factoryId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(name = "mass_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal massKg;

    @Column(name = "piece_count", nullable = false)
    private Integer pieceCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LotUnit unit;

    @Column(name = "claimed_attribute", length = 80)
    private String claimedAttribute;

    @Column(name = "claimed_pct", nullable = false, precision = 7, scale = 4)
    private BigDecimal claimedPct = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LotStatus status = LotStatus.CREATED;

    @Column(nullable = false)
    private boolean orphan;

    @Column(name = "event_sequence", nullable = false)
    private long eventSequence = 0L;

    @Column(name = "latest_event_hash", nullable = false, length = 64)
    private String latestEventHash = HashUtils.GENESIS_HASH;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Lot() {
    }

    public Lot(
            String lotCode,
            LotType lotType,
            String materialType,
            String styleCode,
            String brandCode,
            Long supplierId,
            Long factoryId,
            BigDecimal quantity,
            BigDecimal massKg,
            int pieceCount,
            LotUnit unit,
            String claimedAttribute,
            BigDecimal claimedPct,
            boolean orphan) {
        Instant now = Instant.now();
        this.lotCode = lotCode;
        this.lotType = lotType;
        this.materialType = materialType;
        this.styleCode = styleCode;
        this.brandCode = brandCode;
        this.supplierId = supplierId;
        this.factoryId = factoryId;
        this.quantity = quantity;
        this.massKg = massKg;
        this.pieceCount = pieceCount;
        this.unit = unit;
        this.claimedAttribute = claimedAttribute;
        this.claimedPct = claimedPct != null ? claimedPct : BigDecimal.ZERO;
        this.status = orphan ? LotStatus.FLAGGED : LotStatus.CREATED;
        this.orphan = orphan;
        this.eventSequence = 0L;
        this.latestEventHash = HashUtils.GENESIS_HASH;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyEventState(EventType eventType, boolean flagged, long nextSeq, String eventHash) {
        LotState currentState = LotState.of(this.status);
        currentState.assertCanTransform(eventType);
        this.status = currentState.onEvent(eventType, flagged);
        this.eventSequence = nextSeq;
        this.latestEventHash = eventHash;
        this.updatedAt = Instant.now();
    }

    public void adjustQuantityAndClaim(BigDecimal newQty, BigDecimal newMassKg, Integer newPieceCount,
                                     String claimedAttribute, BigDecimal claimedPct, boolean flagged) {
        if (newQty != null) {
            this.quantity = newQty;
        }
        if (newMassKg != null) {
            this.massKg = newMassKg;
        }
        if (newPieceCount != null) {
            this.pieceCount = newPieceCount;
        }
        if (claimedAttribute != null) {
            this.claimedAttribute = claimedAttribute;
        }
        if (claimedPct != null) {
            this.claimedPct = claimedPct;
        }
        if (flagged) {
            this.status = LotStatus.FLAGGED;
        }
        this.updatedAt = Instant.now();
    }

    public void markFlagged() {
        this.status = LotStatus.FLAGGED;
        this.updatedAt = Instant.now();
    }

    public void markQuarantined() {
        this.status = LotStatus.QUARANTINED;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getLotCode() {
        return lotCode;
    }

    public LotType getLotType() {
        return lotType;
    }

    public String getMaterialType() {
        return materialType;
    }

    public String getStyleCode() {
        return styleCode;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public Long getSupplierId() {
        return supplierId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getMassKg() {
        return massKg;
    }

    public Integer getPieceCount() {
        return pieceCount;
    }

    public LotUnit getUnit() {
        return unit;
    }

    public String getClaimedAttribute() {
        return claimedAttribute;
    }

    public BigDecimal getClaimedPct() {
        return claimedPct;
    }

    public LotStatus getStatus() {
        return status;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public long getEventSequence() {
        return eventSequence;
    }

    public String getLatestEventHash() {
        return latestEventHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
