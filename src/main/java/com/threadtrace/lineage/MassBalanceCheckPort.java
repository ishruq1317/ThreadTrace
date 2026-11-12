package com.threadtrace.lineage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dependency-inversion port implemented by compliance module so lineage remains acyclic.
 */
public interface MassBalanceCheckPort {

    MassBalanceDecision evaluateTransformation(
            String processStep,
            List<Lot> inputLots,
            BigDecimal totalInputQty,
            BigDecimal totalOutputQty,
            LotUnit unit,
            String claimedAttribute,
            BigDecimal claimedPct,
            BigDecimal outputMassKg);

    record MassBalanceDecision(boolean flagged, String reason) {
        public static MassBalanceDecision ok() {
            return new MassBalanceDecision(false, null);
        }
    }
}
