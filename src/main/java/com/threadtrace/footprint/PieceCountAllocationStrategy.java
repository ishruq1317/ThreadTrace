package com.threadtrace.footprint;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.Lot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class PieceCountAllocationStrategy implements AllocationStrategy {

    @Override
    public ThreadTraceProperties.AllocationMethod method() {
        return ThreadTraceProperties.AllocationMethod.BY_PIECE_COUNT;
    }

    @Override
    public BigDecimal allocationFraction(Lot parentLot, LineageNode parentEdgeView) {
        int parentPieces = parentLot.getPieceCount();
        int allocatedPieces = parentEdgeView.getAllocatedPiecesFromParent();
        if (parentPieces > 0 && allocatedPieces > 0) {
            return BigDecimal.valueOf(allocatedPieces)
                    .divide(BigDecimal.valueOf(parentPieces), 6, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);
        }
        BigDecimal parentMass = parentLot.getMassKg();
        if (parentMass != null && parentMass.compareTo(BigDecimal.ZERO) > 0
                && parentEdgeView.getAllocatedMassKgFromParent() != null) {
            return parentEdgeView.getAllocatedMassKgFromParent()
                    .divide(parentMass, 6, RoundingMode.HALF_UP)
                    .min(BigDecimal.ONE);
        }
        return BigDecimal.ONE;
    }
}
