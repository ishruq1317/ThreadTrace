package com.threadtrace.footprint;

import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.LineageVisitor;
import com.threadtrace.lineage.Lot;
import com.threadtrace.lineage.LotCompositeNode;
import com.threadtrace.lineage.LotEvent;
import com.threadtrace.lineage.LotLeafNode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Visitor Pattern implementation for rolling up water, energy, and CO2e footprints (FR6).
 */
public class FootprintRollupVisitor implements LineageVisitor {

    private final AllocationStrategy allocationStrategy;
    private final Map<Long, FootprintTotals> totalsByLotId = new HashMap<>();
    private LineageNode rootNode;

    public FootprintRollupVisitor(AllocationStrategy allocationStrategy) {
        this.allocationStrategy = allocationStrategy;
    }

    @Override
    public void visitLeaf(LotLeafNode leaf) {
        this.rootNode = leaf;
        FootprintTotals own = sumOwnEvents(leaf.getEvents());
        totalsByLotId.put(leaf.getLot().getId(), own);
    }

    @Override
    public void visitComposite(LotCompositeNode composite) {
        this.rootNode = composite;
        FootprintTotals own = sumOwnEvents(composite.getEvents());

        BigDecimal water = own.waterLiters();
        BigDecimal energy = own.energyKwh();
        BigDecimal co2e = own.co2eKg();

        for (LineageNode parent : composite.getParents()) {
            Lot parentLot = parent.getLot();
            FootprintTotals parentTotals = totalsByLotId.getOrDefault(parentLot.getId(), FootprintTotals.ZERO);
            BigDecimal fraction = allocationStrategy.allocationFraction(parentLot, parent);
            water = water.add(parentTotals.waterLiters().multiply(fraction));
            energy = energy.add(parentTotals.energyKwh().multiply(fraction));
            co2e = co2e.add(parentTotals.co2eKg().multiply(fraction));
        }

        totalsByLotId.put(composite.getLot().getId(), new FootprintTotals(
                water.setScale(4, RoundingMode.HALF_UP),
                energy.setScale(4, RoundingMode.HALF_UP),
                co2e.setScale(4, RoundingMode.HALF_UP)
        ));
    }

    private FootprintTotals sumOwnEvents(List<LotEvent> events) {
        BigDecimal water = BigDecimal.ZERO;
        BigDecimal energy = BigDecimal.ZERO;
        BigDecimal co2e = BigDecimal.ZERO;
        for (LotEvent ev : events) {
            water = water.add(ev.getWaterLiters());
            energy = energy.add(ev.getEnergyKwh());
            co2e = co2e.add(ev.getCo2eKg());
        }
        return new FootprintTotals(water, energy, co2e);
    }

    public FootprintSummary getSummary() {
        if (rootNode == null) {
            return new FootprintSummary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, allocationStrategy.method().name());
        }
        Lot rootLot = rootNode.getLot();
        FootprintTotals totals = totalsByLotId.getOrDefault(rootLot.getId(), FootprintTotals.ZERO);
        int pieces = rootLot.getPieceCount() > 0 ? rootLot.getPieceCount() : 1;
        BigDecimal divisor = BigDecimal.valueOf(pieces);
        return new FootprintSummary(
                totals.waterLiters().setScale(4, RoundingMode.HALF_UP),
                totals.energyKwh().setScale(4, RoundingMode.HALF_UP),
                totals.co2eKg().setScale(4, RoundingMode.HALF_UP),
                totals.waterLiters().divide(divisor, 4, RoundingMode.HALF_UP),
                totals.energyKwh().divide(divisor, 4, RoundingMode.HALF_UP),
                totals.co2eKg().divide(divisor, 4, RoundingMode.HALF_UP),
                rootLot.getPieceCount(),
                allocationStrategy.method().name()
        );
    }

    public record FootprintTotals(BigDecimal waterLiters, BigDecimal energyKwh, BigDecimal co2eKg) {
        static final FootprintTotals ZERO = new FootprintTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public record FootprintSummary(
            BigDecimal totalWaterLiters,
            BigDecimal totalEnergyKwh,
            BigDecimal totalCo2eKg,
            BigDecimal waterLitersPerPiece,
            BigDecimal energyKwhPerPiece,
            BigDecimal co2eKgPerPiece,
            int pieceCount,
            String allocationMethod
    ) {
    }
}
