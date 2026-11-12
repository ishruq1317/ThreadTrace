package com.threadtrace;

import com.threadtrace.compliance.ClaimRollupVisitor;
import com.threadtrace.evidence.Certificate;
import com.threadtrace.evidence.CertificateStatus;
import com.threadtrace.footprint.FootprintRollupVisitor;
import com.threadtrace.footprint.MassAllocationStrategy;
import com.threadtrace.footprint.PieceCountAllocationStrategy;
import com.threadtrace.lineage.EventType;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotCompositeNode;
import com.threadtrace.lineage.LotEvent;
import com.threadtrace.lineage.LotLeafNode;
import com.threadtrace.lineage.LotType;
import com.threadtrace.lineage.LotUnit;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FootprintAndClaimVisitorTest {

    @Test
    void allocationStrategiesAllocateDifferentlyAcrossSplitsAndMerges() throws Exception {
        Lot leafLot = createLotWithId(1L, "FAB-1", LotType.FABRIC, BigDecimal.valueOf(100), 200, "RECYCLED", BigDecimal.valueOf(50));
        LotEvent leafEvt = new LotEvent("E1", 1L, "FAB-1", EventType.RECEIVE, "receive", 1L, null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), LotUnit.KG,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                null, 1L, "0".repeat(64), false, null, null, "op", Instant.now());

        // Child took 50% by mass (50 kg of 100 kg), but 75% by piece count (150 pieces of 200 pieces)
        Lot childLot = createLotWithId(2L, "SHP-1", LotType.SHIPMENT, BigDecimal.valueOf(150), 150, "RECYCLED", BigDecimal.valueOf(50));
        LotEvent childEvt = new LotEvent("E2", 2L, "SHP-1", EventType.SEW, "sew", 2L, null,
                BigDecimal.valueOf(150), BigDecimal.valueOf(150), LotUnit.PIECES,
                BigDecimal.valueOf(300), BigDecimal.valueOf(60), BigDecimal.valueOf(30),
                null, 1L, "0".repeat(64), false, null, null, "op", Instant.now());

        LotLeafNode leafNode = new LotLeafNode(leafLot, List.of(leafEvt), BigDecimal.valueOf(50), 150);
        LotCompositeNode root = new LotCompositeNode(childLot, List.of(childEvt), BigDecimal.valueOf(50), 150);
        root.addParent(leafNode);

        FootprintRollupVisitor byMass = new FootprintRollupVisitor(new MassAllocationStrategy());
        root.accept(byMass);
        assertEquals(0, byMass.getSummary().totalWaterLiters().compareTo(BigDecimal.valueOf(800)));

        FootprintRollupVisitor byPieces = new FootprintRollupVisitor(new PieceCountAllocationStrategy());
        root.accept(byPieces);
        assertEquals(0, byPieces.getSummary().totalWaterLiters().compareTo(BigDecimal.valueOf(1050)));
    }

    @Test
    void claimRollupVisitorDropsClaimsWhenCertificateExpired() throws Exception {
        Lot leafLot = createLotWithId(1L, "FAB-1", LotType.FABRIC, BigDecimal.valueOf(100), 0, "ORGANIC", BigDecimal.valueOf(100));
        LotLeafNode leafNode = new LotLeafNode(leafLot, List.of(), BigDecimal.valueOf(100), 0);

        Certificate cert = new Certificate("CERT-1", "GOTS", "Control Union", "Organic cotton",
                "ORGANIC", BigDecimal.valueOf(100), null, 1L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1),
                CertificateStatus.ACTIVE, "k", "f.pdf", "application/pdf", 10);

        ClaimRollupVisitor validVisitor = new ClaimRollupVisitor(List.of(cert), LocalDate.of(2025, 6, 1));
        leafNode.accept(validVisitor);
        assertEquals(0, validVisitor.getVerifiedClaimPercentages().get("ORGANIC").compareTo(BigDecimal.valueOf(100)));
        assertTrue(validVisitor.getViolations().isEmpty());

        ClaimRollupVisitor expiredVisitor = new ClaimRollupVisitor(List.of(cert), LocalDate.of(2026, 2, 1));
        leafNode.accept(expiredVisitor);
        assertTrue(expiredVisitor.getVerifiedClaimPercentages().isEmpty());
        assertFalse(expiredVisitor.getViolations().isEmpty());
    }

    @Test
    void diamondSplitMergeDagDoesNotDoubleCountFootprintOrClaims() throws Exception {
        Lot parent = createLotWithId(10L, "FAB-D", LotType.FABRIC, BigDecimal.valueOf(100), 0, "ORGANIC", BigDecimal.valueOf(80));
        LotEvent parentEvt = new LotEvent("ED0", 10L, "FAB-D", EventType.RECEIVE, "receive", 1L, null,
                BigDecimal.valueOf(100), BigDecimal.valueOf(100), LotUnit.KG,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                null, 1L, "0".repeat(64), false, null, null, "op", Instant.now());

        Lot s1 = createLotWithId(11L, "S1", LotType.FABRIC, BigDecimal.valueOf(60), 0, "ORGANIC", BigDecimal.valueOf(80));
        Lot s2 = createLotWithId(12L, "S2", LotType.FABRIC, BigDecimal.valueOf(40), 0, "ORGANIC", BigDecimal.valueOf(80));
        Lot merged = createLotWithId(13L, "MERGED", LotType.SEWN_GARMENT, BigDecimal.valueOf(100), 200, "ORGANIC", BigDecimal.valueOf(80));

        // Shared parent node (same LotLeafNode instance as produced by memoization in LineageCompositeBuilder)
        LotLeafNode sharedParentNode = new LotLeafNode(parent, List.of(parentEvt), BigDecimal.valueOf(60), 0);

        LotCompositeNode n1 = new LotCompositeNode(s1, List.of(), BigDecimal.valueOf(60), 0);
        n1.addParent(sharedParentNode, BigDecimal.valueOf(60), 0);

        LotCompositeNode n2 = new LotCompositeNode(s2, List.of(), BigDecimal.valueOf(40), 0);
        n2.addParent(sharedParentNode, BigDecimal.valueOf(40), 0);

        LotCompositeNode root = new LotCompositeNode(merged, List.of(), BigDecimal.valueOf(100), 200);
        root.addParent(n1, BigDecimal.valueOf(60), 0);
        root.addParent(n2, BigDecimal.valueOf(40), 0);

        FootprintRollupVisitor fp = new FootprintRollupVisitor(new MassAllocationStrategy());
        root.accept(fp);
        // Must equal 600 + 400 = 1000 L (NOT 600 + 600 = 1200 L)
        assertEquals(0, fp.getSummary().totalWaterLiters().compareTo(BigDecimal.valueOf(1000)));
        assertEquals(0, fp.getSummary().totalCo2eKg().compareTo(BigDecimal.valueOf(50)));

        Certificate cert = new Certificate("CERT-D", "GOTS", "Control Union", "Organic",
                "ORGANIC", BigDecimal.valueOf(80), 10L, null,
                LocalDate.of(2025, 1, 1), LocalDate.of(2027, 1, 1),
                CertificateStatus.ACTIVE, "k", "f.pdf", "application/pdf", 10);
        ClaimRollupVisitor cv = new ClaimRollupVisitor(List.of(cert), LocalDate.of(2026, 6, 1));
        root.accept(cv);
        assertEquals(0, cv.getVerifiedClaimPercentages().get("ORGANIC").compareTo(BigDecimal.valueOf(80)));
        assertTrue(cv.getViolations().isEmpty());
    }

    private static Lot createLotWithId(Long id, String code, LotType type, BigDecimal massKg, int pieces,
                                     String claimAttr, BigDecimal claimPct) throws Exception {
        Lot lot = new Lot(code, type, "Cotton", "ST-1", "BRAND_A", 10L, 20L,
                massKg, massKg, pieces, LotUnit.KG, claimAttr, claimPct, false);
        var idField = Lot.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(lot, id);
        return lot;
    }
}
