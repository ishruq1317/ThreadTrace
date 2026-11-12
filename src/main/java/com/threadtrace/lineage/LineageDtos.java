package com.threadtrace.lineage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class LineageDtos {

    private LineageDtos() {
    }

    public record ReceiveLotRequest(
            @NotBlank @Size(max = 80) String lotCode,
            @NotNull LotType lotType,
            @NotBlank @Size(max = 100) String materialType,
            @Size(max = 80) String styleCode,
            @NotBlank @Size(max = 64) String brandCode,
            @Size(max = 64) String supplierCode,
            @Size(max = 64) String factoryCode,
            @Size(max = 64) String productionLineCode,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0.0001") BigDecimal massKg,
            @Min(0) int pieceCount,
            @NotNull LotUnit unit,
            @Size(max = 80) String claimedAttribute,
            @DecimalMin("0.0") BigDecimal claimedPct,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            @Size(max = 500) String notes,
            Instant occurredAt
    ) {
    }

    public record TransformLotRequest(
            @NotBlank @Size(max = 80) String inputLotCode,
            @NotBlank @Size(max = 80) String outputLotCode,
            @NotNull LotType outputLotType,
            @NotNull EventType eventType,
            @Size(max = 64) String processStep,
            @Size(max = 80) String styleCode,
            @Size(max = 64) String factoryCode,
            @Size(max = 64) String productionLineCode,
            @NotNull @DecimalMin("0.0001") BigDecimal inputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal outputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal outputMassKg,
            @Min(0) int outputPieceCount,
            @NotNull LotUnit unit,
            @Size(max = 80) String claimedAttribute,
            @DecimalMin("0.0") BigDecimal claimedPct,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            @Size(max = 500) String notes,
            Instant occurredAt
    ) {
    }

    public record SplitOutputItem(
            @NotBlank @Size(max = 80) String outputLotCode,
            @NotNull LotType outputLotType,
            @Size(max = 80) String styleCode,
            @NotNull @DecimalMin("0.0001") BigDecimal outputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal outputMassKg,
            @Min(0) int outputPieceCount,
            @NotNull LotUnit unit,
            @Size(max = 80) String claimedAttribute,
            @DecimalMin("0.0") BigDecimal claimedPct
    ) {
    }

    public record SplitLotRequest(
            @NotBlank @Size(max = 80) String parentLotCode,
            @Size(max = 64) String processStep,
            @Size(max = 64) String factoryCode,
            @Size(max = 64) String productionLineCode,
            @NotNull @DecimalMin("0.0001") BigDecimal inputQuantity,
            @NotEmpty @Valid List<SplitOutputItem> outputs,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            @Size(max = 500) String notes,
            Instant occurredAt
    ) {
    }

    public record MergeInputItem(
            @NotBlank @Size(max = 80) String parentLotCode,
            @NotNull @DecimalMin("0.0001") BigDecimal inputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal inputMassKg,
            @Min(0) int inputPieceCount
    ) {
    }

    public record MergeLotRequest(
            @NotEmpty @Valid List<MergeInputItem> inputs,
            @NotBlank @Size(max = 80) String outputLotCode,
            @NotNull LotType outputLotType,
            @NotBlank @Size(max = 100) String materialType,
            @Size(max = 80) String styleCode,
            @Size(max = 64) String processStep,
            @Size(max = 64) String factoryCode,
            @Size(max = 64) String productionLineCode,
            @NotNull @DecimalMin("0.0001") BigDecimal outputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal outputMassKg,
            @Min(0) int outputPieceCount,
            @NotNull LotUnit unit,
            @Size(max = 80) String claimedAttribute,
            @DecimalMin("0.0") BigDecimal claimedPct,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            @Size(max = 500) String notes,
            Instant occurredAt
    ) {
    }

    public record CorrectionEventRequest(
            @NotBlank @Size(max = 80) String lotCode,
            @NotBlank @Size(max = 80) String correctsEventCode,
            @Size(max = 64) String processStep,
            @NotNull @DecimalMin("0.0001") BigDecimal correctedInputQuantity,
            @NotNull @DecimalMin("0.0001") BigDecimal correctedOutputQuantity,
            @DecimalMin("0.0001") BigDecimal correctedMassKg,
            @Min(0) Integer correctedPieceCount,
            @NotNull LotUnit unit,
            @Size(max = 80) String correctedClaimedAttribute,
            @DecimalMin("0.0") BigDecimal correctedClaimedPct,
            boolean clearFlag,
            @NotBlank @Size(max = 500) String notes,
            Instant occurredAt
    ) {
    }

    public record LotView(
            Long id,
            String lotCode,
            LotType lotType,
            String materialType,
            String styleCode,
            String brandCode,
            BigDecimal quantity,
            BigDecimal massKg,
            int pieceCount,
            LotUnit unit,
            String claimedAttribute,
            BigDecimal claimedPct,
            LotStatus status,
            boolean orphan,
            long eventSequence,
            String latestEventHash,
            Instant createdAt
    ) {
    }

    public record LotEventView(
            Long id,
            String eventCode,
            Long lotId,
            EventType eventType,
            String processStep,
            BigDecimal inputQuantity,
            BigDecimal outputQuantity,
            LotUnit unit,
            BigDecimal waterLiters,
            BigDecimal energyKwh,
            BigDecimal co2eKg,
            Long correctsEventId,
            long sequenceNumber,
            String previousHash,
            String eventHash,
            boolean flagged,
            String flagReason,
            String notes,
            String recordedBy,
            Instant occurredAt,
            Instant recordedAt
    ) {
    }

    public record TraceResponse(
            String targetLotCode,
            String direction,
            String brandCode,
            int stepCount,
            List<TraceStepView> steps
    ) {
    }

    public record TraceStepView(
            int depth,
            String parentLotCode,
            String parentLotType,
            String parentMaterialType,
            String parentStatus,
            String supplierCode,
            String supplierName,
            String supplierCountry,
            String supplierRegion,
            String childLotCode,
            String childLotType,
            String eventCode,
            String eventType,
            String processStep,
            BigDecimal allocatedQuantity,
            String unit,
            BigDecimal allocatedMassKg,
            int allocatedPieces,
            String path
    ) {
    }

    public record LotChainVerificationResult(
            String lotCode,
            boolean verified,
            long eventCount,
            String latestHash,
            String failureReason
    ) {
    }
}
