package com.threadtrace.passport;

import com.threadtrace.footprint.FootprintRollupVisitor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builder Pattern for assembling extensible Digital Product Passports (FR7, QA5).
 */
public class ProductPassportBuilder {

    private String passportCode;
    private int version;
    private String styleCode;
    private String styleName;
    private String shipmentLotCode;
    private String brandCode;
    private PassportStatus status = PassportStatus.PUBLISHED;
    private final List<PassportDtos.FibreComponent> fibreComposition = new ArrayList<>();
    private final List<PassportDtos.OriginStep> originSteps = new ArrayList<>();
    private final List<PassportDtos.CertificationSummary> certifications = new ArrayList<>();
    private String careAndRepair;
    private String endOfLifeGuidance;
    private FootprintRollupVisitor.FootprintSummary footprint;
    private final Map<String, String> additionalFields = new LinkedHashMap<>();
    private final List<String> complianceViolations = new ArrayList<>();
    private Instant generatedAt = Instant.now();

    public ProductPassportBuilder identity(
            String passportCode,
            int version,
            String styleCode,
            String styleName,
            String shipmentLotCode,
            String brandCode) {
        this.passportCode = passportCode;
        this.version = version;
        this.styleCode = styleCode;
        this.styleName = styleName;
        this.shipmentLotCode = shipmentLotCode;
        this.brandCode = brandCode;
        return this;
    }

    public ProductPassportBuilder status(PassportStatus status) {
        this.status = status;
        return this;
    }

    public ProductPassportBuilder addFibreComponent(PassportDtos.FibreComponent component) {
        this.fibreComposition.add(component);
        return this;
    }

    public ProductPassportBuilder addOriginStep(PassportDtos.OriginStep step) {
        this.originSteps.add(step);
        return this;
    }

    public ProductPassportBuilder addCertification(PassportDtos.CertificationSummary certification) {
        this.certifications.add(certification);
        return this;
    }

    public ProductPassportBuilder careAndRepair(String careAndRepair) {
        this.careAndRepair = careAndRepair;
        return this;
    }

    public ProductPassportBuilder endOfLifeGuidance(String endOfLifeGuidance) {
        this.endOfLifeGuidance = endOfLifeGuidance;
        return this;
    }

    public ProductPassportBuilder footprint(FootprintRollupVisitor.FootprintSummary footprint) {
        this.footprint = footprint;
        return this;
    }

    public ProductPassportBuilder additionalFields(Map<String, String> fields) {
        if (fields != null) {
            this.additionalFields.putAll(fields);
        }
        return this;
    }

    public ProductPassportBuilder complianceViolations(List<String> violations) {
        if (violations != null) {
            this.complianceViolations.addAll(violations);
        }
        return this;
    }

    public ProductPassportBuilder generatedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
        return this;
    }

    public PassportDtos.PassportCanonicalPayload build() {
        return new PassportDtos.PassportCanonicalPayload(
                passportCode,
                version,
                styleCode,
                styleName,
                shipmentLotCode,
                brandCode,
                status,
                List.copyOf(fibreComposition),
                List.copyOf(originSteps),
                List.copyOf(certifications),
                careAndRepair,
                endOfLifeGuidance,
                footprint,
                Map.copyOf(additionalFields),
                List.copyOf(complianceViolations),
                generatedAt
        );
    }
}
