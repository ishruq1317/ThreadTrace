package com.threadtrace.compliance;

import com.threadtrace.evidence.Certificate;
import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.LineageVisitor;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotCompositeNode;
import com.threadtrace.lineage.LotEvent;
import com.threadtrace.lineage.LotLeafNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Visitor Pattern implementation for rolling up certified material claims (FR4, FR5)
 * across a LineageNode Composite DAG.
 */
public class ClaimRollupVisitor implements LineageVisitor {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final List<Certificate> allCertificates;
    private final LocalDate asOfDate;

    private final Map<Long, Map<String, BigDecimal>> certifiedMassByLotAndAttr = new HashMap<>();
    private final Set<Certificate> validCertificates = new LinkedHashSet<>();
    private final List<String> violations = new ArrayList<>();
    private LineageNode rootNode;

    public ClaimRollupVisitor(List<Certificate> allCertificates, LocalDate asOfDate) {
        this.allCertificates = allCertificates;
        this.asOfDate = asOfDate;
    }

    @Override
    public void visitLeaf(LotLeafNode leaf) {
        this.rootNode = leaf;
        Lot lot = leaf.getLot();
        if (lot.isOrphan()) {
            violations.add("Orphan lot detected: " + lot.getLotCode() + " has no registered supplier");
        }
        checkFlaggedEvents(leaf.getEvents(), lot);

        Map<String, BigDecimal> verifiedForLot = new LinkedHashMap<>();
        List<Certificate> lotCerts = certificatesForLotOrSupplier(lot);
        for (Certificate cert : lotCerts) {
            if (cert.isValidOn(asOfDate)) {
                validCertificates.add(cert);
            }
        }

        if (lot.getClaimedAttribute() != null && !lot.getClaimedAttribute().isBlank()
                && lot.getClaimedPct().compareTo(BigDecimal.ZERO) > 0) {
            String attr = lot.getClaimedAttribute().trim().toUpperCase();
            BigDecimal supportedPct = lotCerts.stream()
                    .filter(c -> c.isValidOn(asOfDate))
                    .filter(c -> c.getClaimedAttribute() != null && c.getClaimedAttribute().equalsIgnoreCase(attr))
                    .map(Certificate::getCertifiedPct)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (supportedPct.compareTo(BigDecimal.ZERO) <= 0) {
                violations.add("Lot " + lot.getLotCode() + " claims " + lot.getClaimedPct() + "% " + attr
                        + " without valid active certificate evidence");
            } else {
                BigDecimal effectivePct = lot.getClaimedPct().min(supportedPct);
                if (lot.getClaimedPct().compareTo(supportedPct) > 0) {
                    violations.add("Lot " + lot.getLotCode() + " over-claims " + lot.getClaimedPct() + "% " + attr
                            + " (certificate supports max " + supportedPct + "%)");
                }
                BigDecimal certMass = leaf.getAllocatedMassKgFromParent()
                        .multiply(effectivePct)
                        .divide(HUNDRED, 4, RoundingMode.HALF_UP);
                verifiedForLot.put(attr, certMass);
            }
        }
        certifiedMassByLotAndAttr.put(lot.getId(), verifiedForLot);
    }

    @Override
    public void visitComposite(LotCompositeNode composite) {
        this.rootNode = composite;
        Lot lot = composite.getLot();
        checkFlaggedEvents(composite.getEvents(), lot);

        for (Certificate cert : certificatesForLotOrSupplier(lot)) {
            if (cert.isValidOn(asOfDate)) {
                validCertificates.add(cert);
            }
        }

        Map<String, BigDecimal> inputCertifiedByAttr = new LinkedHashMap<>();
        BigDecimal totalParentMass = BigDecimal.ZERO;
        for (LineageNode parent : composite.getParents()) {
            Lot parentLot = parent.getLot();
            BigDecimal parentTotalMass = parentLot.getMassKg().compareTo(BigDecimal.ZERO) > 0
                    ? parentLot.getMassKg()
                    : BigDecimal.ONE;
            BigDecimal shareRatio = parent.getAllocatedMassKgFromParent()
                    .divide(parentTotalMass, 6, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);
            totalParentMass = totalParentMass.add(parent.getAllocatedMassKgFromParent());

            Map<String, BigDecimal> parentVerified = certifiedMassByLotAndAttr.getOrDefault(parentLot.getId(), Map.of());
            for (Map.Entry<String, BigDecimal> entry : parentVerified.entrySet()) {
                BigDecimal contributedMass = entry.getValue().multiply(shareRatio);
                inputCertifiedByAttr.merge(entry.getKey(), contributedMass, BigDecimal::add);
            }
        }

        Map<String, BigDecimal> verifiedForLot = new LinkedHashMap<>();
        if (lot.getClaimedAttribute() != null && !lot.getClaimedAttribute().isBlank()
                && lot.getClaimedPct().compareTo(BigDecimal.ZERO) > 0) {
            String attr = lot.getClaimedAttribute().trim().toUpperCase();
            BigDecimal targetMass = composite.getAllocatedMassKgFromParent()
                    .multiply(lot.getClaimedPct())
                    .divide(HUNDRED, 4, RoundingMode.HALF_UP);
            BigDecimal availableMass = inputCertifiedByAttr.getOrDefault(attr, BigDecimal.ZERO);

            if (targetMass.compareTo(availableMass) > 0) {
                violations.add("Lot " + lot.getLotCode() + " over-claims " + attr + ": claimed mass "
                        + targetMass + " kg exceeds certified input " + availableMass.setScale(4, RoundingMode.HALF_UP) + " kg");
            }
            BigDecimal cappedMass = targetMass.min(availableMass);
            if (cappedMass.compareTo(BigDecimal.ZERO) > 0) {
                verifiedForLot.put(attr, cappedMass);
            }
        } else {
            verifiedForLot.putAll(inputCertifiedByAttr);
        }

        certifiedMassByLotAndAttr.put(lot.getId(), verifiedForLot);
    }

    private void checkFlaggedEvents(List<LotEvent> events, Lot lot) {
        for (LotEvent ev : events) {
            if (ev.isFlagged()) {
                violations.add("Lot " + lot.getLotCode() + " event " + ev.getEventCode() + " flagged: " + ev.getFlagReason());
            }
        }
    }

    private List<Certificate> certificatesForLotOrSupplier(Lot lot) {
        List<Certificate> matches = new ArrayList<>();
        for (Certificate c : allCertificates) {
            boolean lotMatch = c.getLotId() != null && c.getLotId().equals(lot.getId());
            boolean supplierMatch = c.getSupplierId() != null && lot.getSupplierId() != null
                    && c.getSupplierId().equals(lot.getSupplierId());
            if (lotMatch || supplierMatch) {
                matches.add(c);
            }
        }
        return matches;
    }

    public Map<String, BigDecimal> getVerifiedClaimPercentages() {
        if (rootNode == null) {
            return Map.of();
        }
        Lot rootLot = rootNode.getLot();
        BigDecimal totalMass = rootLot.getMassKg().compareTo(BigDecimal.ZERO) > 0
                ? rootLot.getMassKg()
                : BigDecimal.ONE;
        Map<String, BigDecimal> certifiedMass = certifiedMassByLotAndAttr.getOrDefault(rootLot.getId(), Map.of());
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : certifiedMass.entrySet()) {
            BigDecimal pct = entry.getValue()
                    .multiply(HUNDRED)
                    .divide(totalMass, 2, RoundingMode.HALF_UP)
                    .min(HUNDRED);
            if (pct.compareTo(BigDecimal.ZERO) > 0) {
                result.put(entry.getKey(), pct);
            }
        }
        return result;
    }

    public List<Certificate> getValidCertificates() {
        return new ArrayList<>(validCertificates);
    }

    public List<String> getViolations() {
        return List.copyOf(violations);
    }
}
