package com.threadtrace.footprint;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.Lot;

import java.math.BigDecimal;

/**
 * Strategy Pattern for allocating environmental footprints across lot splits and merges (FR6).
 */
public interface AllocationStrategy {

    ThreadTraceProperties.AllocationMethod method();

    BigDecimal allocationFraction(Lot parentLot, BigDecimal allocatedMassKg, int allocatedPieces);

    default BigDecimal allocationFraction(Lot parentLot, LineageNode parentEdgeView) {
        return allocationFraction(
                parentLot,
                parentEdgeView.getAllocatedMassKgFromParent(),
                parentEdgeView.getAllocatedPiecesFromParent());
    }
}
