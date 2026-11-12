package com.threadtrace.compliance;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.evidence.Certificate;
import com.threadtrace.evidence.CertificateRepository;
import com.threadtrace.lineage.LineageCompositeBuilder;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotUnit;
import com.threadtrace.lineage.MassBalanceCheckPort;
import com.threadtrace.shared.AudienceAccessService;
import com.threadtrace.shared.DomainExceptions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class MassBalanceService implements MassBalanceCheckPort {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ThreadTraceProperties properties;
    private final CertificateRepository certificateRepository;
    private final LineageCompositeBuilder compositeBuilder;
    private final AudienceAccessService audienceAccessService;

    public MassBalanceService(
            ThreadTraceProperties properties,
            CertificateRepository certificateRepository,
            LineageCompositeBuilder compositeBuilder,
            AudienceAccessService audienceAccessService) {
        this.properties = properties;
        this.certificateRepository = certificateRepository;
        this.compositeBuilder = compositeBuilder;
        this.audienceAccessService = audienceAccessService;
    }

    @Override
    public MassBalanceDecision evaluateTransformation(
            String processStep,
            List<Lot> inputLots,
            BigDecimal totalInputQty,
            BigDecimal totalOutputQty,
            LotUnit unit,
            String claimedAttribute,
            BigDecimal claimedPct,
            BigDecimal outputMassKg) {

        List<String> issues = new ArrayList<>();
        ThreadTraceProperties.ProcessRuleProperties rule = properties.resolveProcessRule(processStep);

        if (!inputLots.isEmpty()) {
            boolean sameUnit = inputLots.stream().allMatch(l -> l.getUnit() == unit);
            if (sameUnit) {
                if (totalOutputQty.compareTo(totalInputQty) > 0) {
                    issues.add("Mass balance violation in " + processStep + ": output " + totalOutputQty
                            + " exceeds input " + totalInputQty + " " + unit);
                } else if (totalInputQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal lossPct = totalInputQty.subtract(totalOutputQty)
                            .multiply(HUNDRED)
                            .divide(totalInputQty, 4, RoundingMode.HALF_UP);
                    if (lossPct.compareTo(rule.getAllowedLossPct()) > 0) {
                        issues.add("Process loss violation in " + processStep + ": loss " + lossPct
                                + "% exceeds allowed " + rule.getAllowedLossPct() + "%");
                    }
                }
            } else {
                // Cross-unit transformation (e.g. KG fabric cut into PIECES): verify mass conservation
                BigDecimal inputMassKg = inputLots.stream()
                        .map(l -> l.getUnit() == LotUnit.KG ? totalInputQty.min(l.getMassKg()) : l.getMassKg())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (outputMassKg != null && inputMassKg.compareTo(BigDecimal.ZERO) > 0) {
                    if (outputMassKg.compareTo(inputMassKg) > 0) {
                        issues.add("Mass balance violation in " + processStep + ": output mass " + outputMassKg
                                + " kg exceeds input mass " + inputMassKg + " kg");
                    } else {
                        BigDecimal massLossPct = inputMassKg.subtract(outputMassKg)
                                .multiply(HUNDRED)
                                .divide(inputMassKg, 4, RoundingMode.HALF_UP);
                        if (massLossPct.compareTo(rule.getAllowedLossPct()) > 0) {
                            issues.add("Process loss violation in " + processStep + ": mass loss " + massLossPct
                                    + "% exceeds allowed " + rule.getAllowedLossPct() + "%");
                        }
                    }
                }
            }
        }

        if (claimedAttribute != null && !claimedAttribute.isBlank()
                && claimedPct != null && claimedPct.compareTo(BigDecimal.ZERO) > 0) {
            if (!inputLots.isEmpty()) {
                BigDecimal certifiedInputMass = BigDecimal.ZERO;
                for (Lot inputLot : inputLots) {
                    ClaimRollupResult rollup = evaluateClaimsForLot(inputLot, LocalDate.now());
                    BigDecimal pct = rollup.verifiedClaimPercentages().getOrDefault(
                            claimedAttribute.trim().toUpperCase(), BigDecimal.ZERO);
                    BigDecimal inputAvailableMass = inputLot.getMassKg().multiply(pct).divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    certifiedInputMass = certifiedInputMass.add(inputAvailableMass);
                }
                BigDecimal requestedCertifiedMass = (outputMassKg != null ? outputMassKg : BigDecimal.ZERO)
                        .multiply(claimedPct)
                        .divide(HUNDRED, 4, RoundingMode.HALF_UP);
                if (requestedCertifiedMass.compareTo(certifiedInputMass) > 0) {
                    issues.add("Over-claimed " + claimedAttribute + " content: requested " + requestedCertifiedMass
                            + " kg exceeds certified input " + certifiedInputMass + " kg");
                }
            }
        }

        if (issues.isEmpty()) {
            return MassBalanceDecision.ok();
        }
        String joined = String.join("; ", issues);
        if (properties.getMassBalance().getEnforcementMode() == ThreadTraceProperties.EnforcementMode.BLOCK) {
            throw new DomainExceptions.MassBalanceViolationException(joined);
        }
        return new MassBalanceDecision(true, joined);
    }

    public ClaimRollupResult evaluateClaimsForLot(Lot targetLot, LocalDate asOfDate) {
        audienceAccessService.assertBrandAccess(targetLot.getBrandCode());
        return evaluateClaimsInternal(targetLot, asOfDate);
    }

    public ClaimRollupResult evaluateClaimsInternal(Lot targetLot, LocalDate asOfDate) {
        LineageCompositeBuilder.LineageResult lineage = compositeBuilder.buildBackwardTree(targetLot);
        Set<Long> supplierIds = new HashSet<>();
        Set<Long> lotIds = new HashSet<>();
        for (Lot l : lineage.allLotsInLineage()) {
            lotIds.add(l.getId());
            if (l.getSupplierId() != null) {
                supplierIds.add(l.getSupplierId());
            }
        }
        List<Certificate> certs = certificateRepository.findBySupplierIdInOrLotIdIn(
                supplierIds.isEmpty() ? List.of(-1L) : supplierIds,
                lotIds.isEmpty() ? List.of(-1L) : lotIds);

        ClaimRollupVisitor visitor = new ClaimRollupVisitor(certs, asOfDate);
        lineage.root().accept(visitor);
        return new ClaimRollupResult(
                targetLot.getLotCode(),
                visitor.getVerifiedClaimPercentages(),
                visitor.getValidCertificates(),
                visitor.getViolations(),
                lineage
        );
    }

    public record ClaimRollupResult(
            String lotCode,
            Map<String, BigDecimal> verifiedClaimPercentages,
            List<Certificate> validCertificates,
            List<String> violations,
            LineageCompositeBuilder.LineageResult lineage
    ) {
    }
}
