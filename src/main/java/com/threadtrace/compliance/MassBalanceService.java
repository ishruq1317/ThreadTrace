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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(MassBalanceService.class);
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
            List<InputSlice> inputSlices,
            BigDecimal totalInputQty,
            BigDecimal totalOutputQty,
            LotUnit outputUnit,
            BigDecimal outputMassKg,
            List<ClaimTarget> claimTargets) {

        List<String> issues = new ArrayList<>();
        ThreadTraceProperties.ProcessRuleProperties rule = properties.resolveProcessRule(processStep);

        if (!inputSlices.isEmpty()) {
            for (InputSlice slice : inputSlices) {
                if (slice.inputQuantity().compareTo(slice.lot().getQuantity()) > 0) {
                    issues.add("Input quantity " + slice.inputQuantity() + " exceeds available lot quantity "
                            + slice.lot().getQuantity() + " for lot " + slice.lot().getLotCode());
                }
            }

            boolean sameUnit = inputSlices.stream().allMatch(s -> s.lot().getUnit() == outputUnit);
            if (sameUnit) {
                if (totalOutputQty.compareTo(totalInputQty) > 0) {
                    issues.add("Mass balance violation in " + processStep + ": output " + totalOutputQty
                            + " exceeds input " + totalInputQty + " " + outputUnit);
                } else if (totalInputQty.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal lossPct = totalInputQty.subtract(totalOutputQty)
                            .multiply(HUNDRED)
                            .divide(totalInputQty, 4, RoundingMode.HALF_UP);
                    if (lossPct.compareTo(rule.getAllowedLossPct()) > 0) {
                        issues.add("Process loss violation in " + processStep + ": loss " + lossPct
                                + "% exceeds allowed " + rule.getAllowedLossPct() + "%");
                    }
                }
            }

            BigDecimal totalInputMassKg = inputSlices.stream()
                    .map(InputSlice::inputMassKg)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (outputMassKg != null && totalInputMassKg.compareTo(BigDecimal.ZERO) > 0) {
                if (outputMassKg.compareTo(totalInputMassKg) > 0) {
                    issues.add("Mass balance violation in " + processStep + ": output mass " + outputMassKg
                            + " kg exceeds input mass " + totalInputMassKg + " kg");
                } else {
                    BigDecimal massLossPct = totalInputMassKg.subtract(outputMassKg)
                            .multiply(HUNDRED)
                            .divide(totalInputMassKg, 4, RoundingMode.HALF_UP);
                    if (massLossPct.compareTo(rule.getAllowedLossPct()) > 0) {
                        issues.add("Process loss violation in " + processStep + ": mass loss " + massLossPct
                                + "% exceeds allowed " + rule.getAllowedLossPct() + "%");
                    }
                }
            }
        }

        if (!inputSlices.isEmpty() && claimTargets != null && !claimTargets.isEmpty()) {
            Map<String, BigDecimal> requestedByAttr = new java.util.LinkedHashMap<>();
            for (ClaimTarget target : claimTargets) {
                if (target.claimedAttribute() != null && !target.claimedAttribute().isBlank()
                        && target.claimedPct() != null && target.claimedPct().compareTo(BigDecimal.ZERO) > 0) {
                    String attr = target.claimedAttribute().trim().toUpperCase();
                    BigDecimal targetMass = (target.outputMassKg() != null ? target.outputMassKg() : BigDecimal.ZERO)
                            .multiply(target.claimedPct())
                            .divide(HUNDRED, 4, RoundingMode.HALF_UP);
                    requestedByAttr.merge(attr, targetMass, BigDecimal::add);
                }
            }

            if (!requestedByAttr.isEmpty()) {
                Map<String, BigDecimal> availableByAttr = new java.util.LinkedHashMap<>();
                for (InputSlice slice : inputSlices) {
                    ClaimRollupResult rollup = evaluateClaimsInternal(slice.lot(), LocalDate.now());
                    for (Map.Entry<String, BigDecimal> entry : rollup.verifiedClaimPercentages().entrySet()) {
                        BigDecimal sliceCertifiedMass = slice.inputMassKg()
                                .multiply(entry.getValue())
                                .divide(HUNDRED, 4, RoundingMode.HALF_UP);
                        availableByAttr.merge(entry.getKey(), sliceCertifiedMass, BigDecimal::add);
                    }
                }

                for (Map.Entry<String, BigDecimal> reqEntry : requestedByAttr.entrySet()) {
                    BigDecimal available = availableByAttr.getOrDefault(reqEntry.getKey(), BigDecimal.ZERO);
                    if (reqEntry.getValue().compareTo(available) > 0) {
                        issues.add("Over-claimed " + reqEntry.getKey() + " content: requested "
                                + reqEntry.getValue() + " kg exceeds certified input " + available + " kg");
                    }
                }
            }
        }

        if (issues.isEmpty()) {
            log.info("Evaluated mass-balance step={} inputs={} flagged=false", processStep, inputSlices.size());
            return MassBalanceDecision.ok();
        }
        String joined = String.join("; ", issues);
        log.info("Evaluated mass-balance step={} inputs={} flagged=true mode={}",
                processStep, inputSlices.size(), properties.getMassBalance().getEnforcementMode());
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
        log.info("Evaluated claim rollup lot={} asOfDate={} verifiedClaims={} violations={}",
                targetLot.getLotCode(), asOfDate, visitor.getVerifiedClaimPercentages().size(), visitor.getViolations().size());
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
