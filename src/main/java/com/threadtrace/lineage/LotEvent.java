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
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Immutable
@Table(name = "lot_events")
public class LotEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_code", nullable = false, unique = true, length = 80)
    private String eventCode;

    @Column(name = "lot_id", nullable = false)
    private Long lotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Column(name = "process_step", nullable = false, length = 64)
    private String processStep;

    @Column(name = "party_id")
    private Long partyId;

    @Column(name = "production_line_id")
    private Long productionLineId;

    @Column(name = "input_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal inputQuantity;

    @Column(name = "output_quantity", nullable = false, precision = 19, scale = 4)
    private BigDecimal outputQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LotUnit unit;

    @Column(name = "water_liters", nullable = false, precision = 19, scale = 4)
    private BigDecimal waterLiters;

    @Column(name = "energy_kwh", nullable = false, precision = 19, scale = 4)
    private BigDecimal energyKwh;

    @Column(name = "co2e_kg", nullable = false, precision = 19, scale = 4)
    private BigDecimal co2eKg;

    @Column(name = "corrects_event_id")
    private Long correctsEventId;

    @Column(name = "sequence_number", nullable = false)
    private long sequenceNumber;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    @Column(nullable = false)
    private boolean flagged;

    @Column(name = "flag_reason", length = 500)
    private String flagReason;

    @Column(length = 500)
    private String notes;

    @Column(name = "recorded_by", nullable = false, length = 100)
    private String recordedBy;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    protected LotEvent() {
    }

    public LotEvent(
            String eventCode,
            Long lotId,
            String lotCode,
            EventType eventType,
            String processStep,
            Long partyId,
            Long productionLineId,
            BigDecimal inputQuantity,
            BigDecimal outputQuantity,
            LotUnit unit,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            Long correctsEventId,
            long sequenceNumber,
            String previousHash,
            boolean flagged,
            String flagReason,
            String notes,
            String recordedBy,
            Instant occurredAt) {
        this.eventCode = eventCode;
        this.lotId = lotId;
        this.eventType = eventType;
        this.processStep = processStep;
        this.partyId = partyId;
        this.productionLineId = productionLineId;
        this.inputQuantity = inputQuantity;
        this.outputQuantity = outputQuantity;
        this.unit = unit;
        this.waterLiters = waterLiters != null ? waterLiters : BigDecimal.ZERO;
        this.energyKwh = energyKwh != null ? energyKwh : BigDecimal.ZERO;
        this.co2eKg = co2eKg != null ? co2eKg : BigDecimal.ZERO;
        this.correctsEventId = correctsEventId;
        this.sequenceNumber = sequenceNumber;
        this.previousHash = previousHash;
        this.flagged = flagged;
        this.flagReason = flagReason;
        this.notes = notes;
        this.recordedBy = recordedBy;
        this.occurredAt = (occurredAt != null ? occurredAt : Instant.now()).truncatedTo(ChronoUnit.MILLIS);
        this.recordedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        this.eventHash = HashUtils.sha256(canonicalPayload(
                lotCode, sequenceNumber, previousHash, eventType, processStep,
                inputQuantity, outputQuantity, unit, this.waterLiters, this.energyKwh, this.co2eKg,
                flagged, flagReason, this.occurredAt, recordedBy, correctsEventId));
    }

    public static String canonicalPayload(
            String lotCode,
            long sequenceNumber,
            String previousHash,
            EventType eventType,
            String processStep,
            BigDecimal inputQuantity,
            BigDecimal outputQuantity,
            LotUnit unit,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            boolean flagged,
            String flagReason,
            Instant occurredAt,
            String recordedBy,
            Long correctsEventId) {
        return String.join("|",
                lotCode,
                Long.toString(sequenceNumber),
                previousHash,
                eventType.name(),
                processStep,
                inputQuantity.stripTrailingZeros().toPlainString(),
                outputQuantity.stripTrailingZeros().toPlainString(),
                unit.name(),
                waterLiters.stripTrailingZeros().toPlainString(),
                energyKwh.stripTrailingZeros().toPlainString(),
                co2eKg.stripTrailingZeros().toPlainString(),
                Boolean.toString(flagged),
                flagReason == null ? "" : flagReason,
                Long.toString(occurredAt.toEpochMilli()),
                recordedBy,
                correctsEventId == null ? "" : Long.toString(correctsEventId)
        );
    }

    public Long getId() {
        return id;
    }

    public String getEventCode() {
        return eventCode;
    }

    public Long getLotId() {
        return lotId;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getProcessStep() {
        return processStep;
    }

    public Long getPartyId() {
        return partyId;
    }

    public Long getProductionLineId() {
        return productionLineId;
    }

    public BigDecimal getInputQuantity() {
        return inputQuantity;
    }

    public BigDecimal getOutputQuantity() {
        return outputQuantity;
    }

    public LotUnit getUnit() {
        return unit;
    }

    public BigDecimal getWaterLiters() {
        return waterLiters;
    }

    public BigDecimal getEnergyKwh() {
        return energyKwh;
    }

    public BigDecimal getCo2eKg() {
        return co2eKg;
    }

    public Long getCorrectsEventId() {
        return correctsEventId;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public String getFlagReason() {
        return flagReason;
    }

    public String getNotes() {
        return notes;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
