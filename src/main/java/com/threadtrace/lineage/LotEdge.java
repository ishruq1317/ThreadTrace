package com.threadtrace.lineage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Immutable
@Table(name = "lot_edges")
public class LotEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "parent_lot_id", nullable = false)
    private Long parentLotId;

    @Column(name = "child_lot_id", nullable = false)
    private Long childLotId;

    @Column(name = "allocated_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LotUnit unit;

    @Column(name = "allocated_mass_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal allocatedMassKg;

    @Column(name = "allocated_pieces", nullable = false)
    private int allocatedPieces;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LotEdge() {
    }

    public LotEdge(
            Long eventId,
            Long parentLotId,
            Long childLotId,
            BigDecimal allocatedQuantity,
            LotUnit unit,
            BigDecimal allocatedMassKg,
            int allocatedPieces) {
        this.eventId = eventId;
        this.parentLotId = parentLotId;
        this.childLotId = childLotId;
        this.allocatedQuantity = allocatedQuantity;
        this.unit = unit;
        this.allocatedMassKg = allocatedMassKg;
        this.allocatedPieces = allocatedPieces;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getParentLotId() {
        return parentLotId;
    }

    public Long getChildLotId() {
        return childLotId;
    }

    public BigDecimal getAllocatedQuantity() {
        return allocatedQuantity;
    }

    public LotUnit getUnit() {
        return unit;
    }

    public BigDecimal getAllocatedMassKg() {
        return allocatedMassKg;
    }

    public int getAllocatedPieces() {
        return allocatedPieces;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
