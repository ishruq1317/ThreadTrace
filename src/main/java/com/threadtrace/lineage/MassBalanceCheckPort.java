package com.threadtrace.lineage;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dependency-inversion port implemented by compliance module so lineage remains acyclic.
 */
public interface MassBalanceCheckPort {

    MassBalanceDecision evaluateTransformation(
            String processStep,
            List<InputSlice> inputSlices,
            BigDecimal totalInputQty,
            BigDecimal totalOutputQty,
            LotUnit outputUnit,
            BigDecimal outputMassKg,
            List<ClaimTarget> claimTargets);

    record InputSlice(Lot lot, BigDecimal inputQuantity, BigDecimal inputMassKg) {
    }

    record ClaimTarget(String claimedAttribute, BigDecimal claimedPct, BigDecimal outputMassKg) {
    }

    record MassBalanceDecision(boolean flagged, String reason) {
        public static MassBalanceDecision ok() {
            return new MassBalanceDecision(false, null);
        }
    }
}
