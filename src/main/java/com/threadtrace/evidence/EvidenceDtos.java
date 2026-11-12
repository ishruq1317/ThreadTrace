package com.threadtrace.evidence;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class EvidenceDtos {

    private EvidenceDtos() {
    }

    public record RegisterCertificateRequest(
            @NotBlank @Size(max = 100) String certificateNumber,
            @NotBlank @Size(max = 64) String certificateType,
            @NotBlank @Size(max = 200) String issuer,
            @NotBlank @Size(max = 200) String scope,
            @Size(max = 80) String claimedAttribute,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal certifiedPct,
            @Size(max = 64) String supplierCode,
            @Size(max = 80) String lotCode,
            @NotNull LocalDate validFrom,
            @NotNull LocalDate validTo,
            String fileContentBase64,
            @Size(max = 255) String filename,
            @Size(max = 120) String contentType
    ) {
    }

    public record CertificateView(
            Long id,
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
            CertificateStatus status,
            String originalFilename,
            String contentType,
            long fileSizeBytes,
            Instant createdAt
    ) {
    }

    public record ExpiryEvaluationSummary(
            LocalDate asOfDate,
            int expiringSoonCount,
            int expiredCount
    ) {
    }
}
