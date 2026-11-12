package com.threadtrace.passport;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.footprint.FootprintRollupVisitor;
import com.threadtrace.lineage.LineageDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class PassportDtos {

    private PassportDtos() {
    }

    public record GeneratePassportRequest(
            @NotBlank @Size(max = 80) String passportCode,
            @NotBlank @Size(max = 80) String shipmentLotCode,
            @NotBlank @Size(max = 80) String styleCode,
            @NotBlank @Size(max = 200) String styleName,
            @NotBlank @Size(max = 500) String careAndRepair,
            @NotBlank @Size(max = 500) String endOfLifeGuidance,
            ThreadTraceProperties.AllocationMethod allocationMethod,
            Map<String, String> customFields
    ) {
    }

    public record FibreComponent(
            String materialType,
            String verifiedClaim,
            BigDecimal verifiedPercentage
    ) {
    }

    public record OriginStep(
            int depth,
            String processStep,
            String lotCode,
            String partyCode,
            String partyName,
            String partyType,
            String country,
            String region,
            String exactAddress,
            Integer workerCount,
            BigDecimal unitPriceUsd
    ) {
    }

    public record PublicOriginStep(
            int depth,
            String processStep,
            String partyName,
            String partyType,
            String country,
            String region
    ) {
    }

    public record BrandOriginStep(
            int depth,
            String processStep,
            String lotCode,
            String partyCode,
            String partyName,
            String partyType,
            String country,
            String region
    ) {
    }

    public record CertificationSummary(
            String certificateNumber,
            String certificateType,
            String issuer,
            String scope,
            String claimedAttribute,
            LocalDate validFrom,
            LocalDate validTo
    ) {
    }

    public record PassportCanonicalPayload(
            String passportCode,
            int version,
            String styleCode,
            String styleName,
            String shipmentLotCode,
            String brandCode,
            PassportStatus status,
            List<FibreComponent> fibreComposition,
            List<OriginStep> originSteps,
            List<CertificationSummary> certifications,
            String careAndRepair,
            String endOfLifeGuidance,
            FootprintRollupVisitor.FootprintSummary footprint,
            Map<String, String> additionalFields,
            List<String> complianceViolations,
            Instant generatedAt
    ) {
    }

    public record PublicPassportView(
            String passportCode,
            int version,
            String styleCode,
            String styleName,
            String brandCode,
            PassportStatus status,
            List<FibreComponent> fibreComposition,
            List<PublicOriginStep> originByProcessStep,
            List<CertificationSummary> certifications,
            String careAndRepair,
            String endOfLifeGuidance,
            FootprintRollupVisitor.FootprintSummary footprint,
            Map<String, String> additionalFields,
            String qrCodeUrl,
            Instant generatedAt
    ) {
    }

    public record BrandPassportView(
            String passportCode,
            int version,
            String styleCode,
            String styleName,
            String shipmentLotCode,
            String brandCode,
            PassportStatus status,
            List<FibreComponent> fibreComposition,
            List<BrandOriginStep> originByProcessStep,
            List<CertificationSummary> certifications,
            String careAndRepair,
            String endOfLifeGuidance,
            FootprintRollupVisitor.FootprintSummary footprint,
            Map<String, String> additionalFields,
            List<String> complianceViolations,
            Instant generatedAt
    ) {
    }

    public record AuditorPassportView(
            String passportCode,
            int version,
            String styleCode,
            String styleName,
            String shipmentLotCode,
            String brandCode,
            PassportStatus status,
            List<FibreComponent> fibreComposition,
            List<OriginStep> originByProcessStep,
            List<CertificationSummary> certifications,
            String careAndRepair,
            String endOfLifeGuidance,
            FootprintRollupVisitor.FootprintSummary footprint,
            Map<String, String> additionalFields,
            List<String> complianceViolations,
            String contentHash,
            String etag,
            Instant generatedAt
    ) {
    }

    public record PassportVersionSummary(
            String passportCode,
            int version,
            boolean latestVersion,
            PassportStatus status,
            String styleCode,
            String brandCode,
            String contentHash,
            Instant createdAt
    ) {
    }

    public record PassportIntegrityVerificationView(
            String passportCode,
            int version,
            boolean verified,
            int lotsChecked,
            List<LineageDtos.LotChainVerificationResult> lotResults
    ) {
    }
}
