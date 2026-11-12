package com.threadtrace.passport;

import com.threadtrace.compliance.MassBalanceService;
import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.evidence.Certificate;
import com.threadtrace.footprint.FootprintRollupVisitor;
import com.threadtrace.footprint.FootprintService;
import com.threadtrace.lineage.EventIntegrityService;
import com.threadtrace.lineage.LineageDtos;
import com.threadtrace.lineage.LineageQueryRepository;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotRepository;
import com.threadtrace.parties.Party;
import com.threadtrace.parties.PartyRepository;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import com.threadtrace.shared.HashUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PassportService {

    private final ProductPassportRepository passportRepository;
    private final LotRepository lotRepository;
    private final PartyRepository partyRepository;
    private final MassBalanceService massBalanceService;
    private final FootprintService footprintService;
    private final EventIntegrityService integrityService;
    private final AudiencePassportProxy audienceProxy;
    private final AudienceAccessService audienceAccessService;
    private final ResilientPassportCache resilientCache;
    private final ThreadTraceProperties properties;
    private final ObjectMapper objectMapper;

    public PassportService(
            ProductPassportRepository passportRepository,
            LotRepository lotRepository,
            PartyRepository partyRepository,
            MassBalanceService massBalanceService,
            FootprintService footprintService,
            EventIntegrityService integrityService,
            AudiencePassportProxy audienceProxy,
            AudienceAccessService audienceAccessService,
            ResilientPassportCache resilientCache,
            ThreadTraceProperties properties,
            ObjectMapper objectMapper) {
        this.passportRepository = passportRepository;
        this.lotRepository = lotRepository;
        this.partyRepository = partyRepository;
        this.massBalanceService = massBalanceService;
        this.footprintService = footprintService;
        this.integrityService = integrityService;
        this.audienceProxy = audienceProxy;
        this.audienceAccessService = audienceAccessService;
        this.resilientCache = resilientCache;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PassportDtos.AuditorPassportView generatePassport(PassportDtos.GeneratePassportRequest req) {
        Lot shipmentLot = lotRepository.findByLotCode(req.shipmentLotCode())
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException(
                        "Shipment lot not found: " + req.shipmentLotCode()));

        audienceAccessService.assertBrandAccess(shipmentLot.getBrandCode());

        ThreadTraceProperties.AllocationMethod allocationMethod = req.allocationMethod() != null
                ? req.allocationMethod()
                : properties.getFootprint().getDefaultAllocationMethod();

        return buildAndSaveVersion(
                req.passportCode(),
                shipmentLot,
                req.styleCode(),
                req.styleName(),
                req.careAndRepair(),
                req.endOfLifeGuidance(),
                allocationMethod,
                req.customFields(),
                LocalDate.now()
        );
    }

    @Transactional
    public void recalculateAllForExpiredEvidence(Long expiredSupplierId, Long expiredLotId, LocalDate asOfDate) {
        List<ProductPassport> latestPassports = passportRepository.findByLatestVersionTrue();
        for (ProductPassport existing : latestPassports) {
            Lot shipmentLot = lotRepository.findById(existing.getShipmentLotId()).orElse(null);
            if (shipmentLot == null) {
                continue;
            }
            MassBalanceService.ClaimRollupResult rollup = massBalanceService.evaluateClaimsInternal(shipmentLot, asOfDate);
            boolean touchesScope = rollup.lineage().allLotsInLineage().stream()
                    .anyMatch(l -> (expiredLotId != null && expiredLotId.equals(l.getId()))
                            || (expiredSupplierId != null && expiredSupplierId.equals(l.getSupplierId())));
            if (touchesScope) {
                PassportDtos.PassportCanonicalPayload prev = deserialize(existing.getPayloadJson());
                buildAndSaveVersion(
                        existing.getPassportCode(),
                        shipmentLot,
                        existing.getStyleCode(),
                        existing.getStyleName(),
                        prev.careAndRepair(),
                        prev.endOfLifeGuidance(),
                        ThreadTraceProperties.AllocationMethod.valueOf(existing.getAllocationMethod()),
                        prev.additionalFields(),
                        asOfDate
                );
            }
        }
    }

    private PassportDtos.AuditorPassportView buildAndSaveVersion(
            String passportCode,
            Lot shipmentLot,
            String styleCode,
            String styleName,
            String careAndRepair,
            String endOfLifeGuidance,
            ThreadTraceProperties.AllocationMethod allocationMethod,
            Map<String, String> customFields,
            LocalDate asOfDate) {

        int nextVersion = passportRepository.findByPassportCodeAndLatestVersionTrue(passportCode)
                .map(prev -> {
                    if (!prev.getBrandCode().equalsIgnoreCase(shipmentLot.getBrandCode())) {
                        throw new DomainExceptions.BrandAccessDeniedException(
                                "Passport code '" + passportCode + "' belongs to another brand");
                    }
                    prev.markSuperseded();
                    passportRepository.save(prev);
                    return prev.getVersion() + 1;
                })
                .orElse(1);

        MassBalanceService.ClaimRollupResult claimResult = massBalanceService.evaluateClaimsInternal(shipmentLot, asOfDate);
        FootprintRollupVisitor.FootprintSummary footprint = footprintService.calculateForLineageRoot(
                claimResult.lineage().root(), allocationMethod);

        PassportStatus status = claimResult.violations().isEmpty()
                ? PassportStatus.PUBLISHED
                : PassportStatus.CLAIMS_DOWNGRADED;

        ProductPassportBuilder builder = new ProductPassportBuilder()
                .identity(passportCode, nextVersion, styleCode, styleName, shipmentLot.getLotCode(), shipmentLot.getBrandCode())
                .status(status)
                .careAndRepair(careAndRepair)
                .endOfLifeGuidance(endOfLifeGuidance)
                .footprint(footprint)
                .complianceViolations(claimResult.violations())
                .generatedAt(Instant.now());

        Map<String, String> mergedFields = new LinkedHashMap<>(properties.getPassport().getExtraFields());
        if (customFields != null) {
            mergedFields.putAll(customFields);
        }
        builder.additionalFields(mergedFields);

        // Build fibre composition strictly from verified claims (never publish unsupported claims)
        Map<String, BigDecimal> verifiedClaims = claimResult.verifiedClaimPercentages();
        if (verifiedClaims.isEmpty()) {
            boolean hadClaim = claimResult.lineage().allLotsInLineage().stream()
                    .anyMatch(l -> l.getClaimedAttribute() != null && !l.getClaimedAttribute().isBlank()
                            && l.getClaimedPct().compareTo(BigDecimal.ZERO) > 0);
            if (hadClaim || !claimResult.violations().isEmpty()) {
                builder.addFibreComponent(new PassportDtos.FibreComponent(
                        shipmentLot.getMaterialType(),
                        "STANDARD_UNVERIFIED_CLAIM_DROPPED",
                        BigDecimal.ZERO
                ));
            } else {
                builder.addFibreComponent(new PassportDtos.FibreComponent(
                        shipmentLot.getMaterialType(),
                        "STANDARD",
                        BigDecimal.valueOf(100)
                ));
            }
        } else {
            for (Map.Entry<String, BigDecimal> entry : verifiedClaims.entrySet()) {
                builder.addFibreComponent(new PassportDtos.FibreComponent(
                        shipmentLot.getMaterialType(),
                        entry.getKey(),
                        entry.getValue()
                ));
            }
        }

        // Load all parties involved in lineage for origin steps
        Set<Long> partyIds = new LinkedHashSet<>();
        for (Lot lot : claimResult.lineage().allLotsInLineage()) {
            if (lot.getSupplierId() != null) {
                partyIds.add(lot.getSupplierId());
            }
            if (lot.getFactoryId() != null) {
                partyIds.add(lot.getFactoryId());
            }
        }
        Map<Long, Party> partiesById = partyRepository.findAllById(partyIds).stream()
                .collect(Collectors.toMap(Party::getId, Function.identity()));

        Map<Long, Lot> lotsById = claimResult.lineage().allLotsInLineage().stream()
                .collect(Collectors.toMap(Lot::getId, Function.identity()));

        for (LineageQueryRepository.TraceStepRow step : claimResult.lineage().traceSteps()) {
            Lot childLot = lotsById.get(step.childLotId());
            Lot parentLot = lotsById.get(step.parentLotId());
            Long partyId = (childLot != null && childLot.getFactoryId() != null)
                    ? childLot.getFactoryId()
                    : (parentLot != null ? parentLot.getSupplierId() : null);
            Party party = partyId != null ? partiesById.get(partyId) : null;
            builder.addOriginStep(new PassportDtos.OriginStep(
                    step.depth(),
                    step.processStep(),
                    step.childLotCode(),
                    party != null ? party.getCode() : "UNKNOWN",
                    party != null ? party.getName() : "Unregistered Party",
                    party != null ? party.getPartyType().name() : "UNKNOWN",
                    party != null ? party.getCountry() : "UNKNOWN",
                    party != null ? party.getRegion() : "UNKNOWN",
                    party != null ? party.getExactAddress() : null,
                    party != null ? party.getWorkerCount() : null,
                    party != null ? party.getUnitPriceUsd() : null
            ));
        }

        if (claimResult.lineage().traceSteps().isEmpty() && shipmentLot.getSupplierId() != null) {
            Party supplier = partiesById.get(shipmentLot.getSupplierId());
            builder.addOriginStep(new PassportDtos.OriginStep(
                    1,
                    "receive",
                    shipmentLot.getLotCode(),
                    supplier != null ? supplier.getCode() : "UNKNOWN",
                    supplier != null ? supplier.getName() : "Unregistered Party",
                    supplier != null ? supplier.getPartyType().name() : "UNKNOWN",
                    supplier != null ? supplier.getCountry() : "UNKNOWN",
                    supplier != null ? supplier.getRegion() : "UNKNOWN",
                    supplier != null ? supplier.getExactAddress() : null,
                    supplier != null ? supplier.getWorkerCount() : null,
                    supplier != null ? supplier.getUnitPriceUsd() : null
            ));
        }

        for (Certificate cert : claimResult.validCertificates()) {
            builder.addCertification(new PassportDtos.CertificationSummary(
                    cert.getCertificateNumber(),
                    cert.getCertificateType(),
                    cert.getIssuer(),
                    cert.getScope(),
                    cert.getClaimedAttribute(),
                    cert.getValidFrom(),
                    cert.getValidTo()
            ));
        }

        PassportDtos.PassportCanonicalPayload payload = builder.build();
        String json = objectMapper.writeValueAsString(payload);
        String contentHash = HashUtils.sha256(json);
        String etag = "\"" + contentHash.substring(0, 24) + "\"";

        ProductPassport entity = passportRepository.save(new ProductPassport(
                passportCode,
                nextVersion,
                true,
                styleCode,
                styleName,
                shipmentLot.getId(),
                shipmentLot.getBrandCode(),
                status,
                allocationMethod.name(),
                json,
                contentHash,
                etag
        ));

        PassportDtos.PublicPassportView publicView = audienceProxy.toPublicView(payload);
        resilientCache.putLatest(passportCode, publicView, etag);

        return audienceProxy.toAuditorView(entity, payload);
    }

    public Object getPassportForAudience(String passportCode, Integer version) {
        ProductPassport entity = findPassport(passportCode, version);
        PassportDtos.PassportCanonicalPayload payload = deserialize(entity.getPayloadJson());
        return audienceProxy.projectForCurrentAudience(entity, payload);
    }

    public PublicPassportResponse getPublicPassportWithFallback(String passportCode, Integer version) {
        try {
            ProductPassport entity = findPassport(passportCode, version);
            PassportDtos.PassportCanonicalPayload payload = deserialize(entity.getPayloadJson());
            PassportDtos.PublicPassportView publicView = audienceProxy.toPublicView(payload);
            if (version == null) {
                resilientCache.putLatest(passportCode, publicView, entity.getEtag());
            } else {
                resilientCache.putVersion(passportCode, version, publicView, entity.getEtag());
            }
            return new PublicPassportResponse(publicView, entity.getEtag(), false);
        } catch (DataAccessException ex) {
            return resilientCache.get(passportCode, version)
                    .map(cached -> new PublicPassportResponse(cached.view(), cached.etag(), true))
                    .orElseThrow(() -> ex);
        }
    }

    public Page<PassportDtos.PassportVersionSummary> listLatestPassports(Pageable pageable) {
        var audience = audienceAccessService.resolveAudience();
        Page<ProductPassport> page = audience == com.threadtrace.shared.Audience.BRAND
                ? passportRepository.findByBrandCodeAndLatestVersionTrue(
                        audienceAccessService.currentBrandScope().orElse(""), pageable)
                : passportRepository.findByLatestVersionTrue(pageable);
        return page.map(this::toVersionSummary);
    }

    public Page<PassportDtos.PassportVersionSummary> listPassportVersions(String passportCode, Pageable pageable) {
        Page<ProductPassport> versions = passportRepository.findByPassportCodeOrderByVersionDesc(passportCode, pageable);
        if (versions.isEmpty()) {
            throw new DomainExceptions.ResourceNotFoundException("Passport not found: " + passportCode);
        }
        audienceAccessService.assertBrandAccess(versions.getContent().getFirst().getBrandCode());
        return versions.map(this::toVersionSummary);
    }

    public PassportDtos.PassportIntegrityVerificationView verifyPassportIntegrity(String passportCode) {
        ProductPassport passport = passportRepository.findByPassportCodeAndLatestVersionTrue(passportCode)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Passport not found: " + passportCode));
        audienceAccessService.assertBrandAccess(passport.getBrandCode());

        Lot shipmentLot = lotRepository.findById(passport.getShipmentLotId())
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Shipment lot missing"));

        MassBalanceService.ClaimRollupResult rollup = massBalanceService.evaluateClaimsInternal(shipmentLot, LocalDate.now());
        List<LineageDtos.LotChainVerificationResult> lotResults = integrityService.verifyLots(rollup.lineage().allLotsInLineage());
        boolean allVerified = lotResults.stream().allMatch(LineageDtos.LotChainVerificationResult::verified);

        return new PassportDtos.PassportIntegrityVerificationView(
                passport.getPassportCode(),
                passport.getVersion(),
                allVerified,
                lotResults.size(),
                lotResults
        );
    }

    private ProductPassport findPassport(String passportCode, Integer version) {
        if (version == null) {
            return passportRepository.findByPassportCodeAndLatestVersionTrue(passportCode)
                    .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException("Passport not found: " + passportCode));
        }
        return passportRepository.findByPassportCodeAndVersion(passportCode, version)
                .orElseThrow(() -> new DomainExceptions.ResourceNotFoundException(
                        "Passport not found: " + passportCode + " version " + version));
    }

    private PassportDtos.PassportVersionSummary toVersionSummary(ProductPassport p) {
        return new PassportDtos.PassportVersionSummary(
                p.getPassportCode(), p.getVersion(), p.isLatestVersion(), p.getStatus(),
                p.getStyleCode(), p.getBrandCode(), p.getContentHash(), p.getCreatedAt());
    }

    private PassportDtos.PassportCanonicalPayload deserialize(String json) {
        return objectMapper.readValue(json, PassportDtos.PassportCanonicalPayload.class);
    }

    public record PublicPassportResponse(
            PassportDtos.PublicPassportView view,
            String etag,
            boolean servedFromFallbackCache
    ) {
    }
}
