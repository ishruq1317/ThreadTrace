package com.threadtrace.parties;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PartyDtos {

    private PartyDtos() {
    }

    public record CreatePartyRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 200) String name,
            @NotNull PartyType partyType,
            @NotBlank @Size(max = 100) String country,
            @NotBlank @Size(max = 150) String region,
            @Size(max = 500) String exactAddress,
            @Min(1) Integer workerCount,
            @DecimalMin("0.0") BigDecimal unitPriceUsd
    ) {
    }

    public record CreateProductionLineRequest(
            @NotBlank @Size(max = 64) String lineCode,
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 64) String processType,
            @Size(max = 255) String locationDetail
    ) {
    }

    public record PartyPublicView(
            String code,
            String name,
            PartyType partyType,
            String country,
            String region
    ) {
    }

    public record PartyBrandView(
            String code,
            String name,
            PartyType partyType,
            String country,
            String region
    ) {
    }

    public record PartyAuditorView(
            Long id,
            String code,
            String name,
            PartyType partyType,
            String country,
            String region,
            String exactAddress,
            Integer workerCount,
            BigDecimal unitPriceUsd,
            Instant createdAt
    ) {
    }

    public record ProductionLineView(
            Long id,
            String lineCode,
            String name,
            String factoryCode,
            String processType,
            String locationDetail,
            Instant createdAt
    ) {
    }
}
