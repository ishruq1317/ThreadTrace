package com.threadtrace.footprint;

import com.threadtrace.config.ThreadTraceProperties;
import com.threadtrace.lineage.LineageNode;
import com.threadtrace.lineage.Lot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class MassAllocationStrategy implements AllocationStrategy {

    @Override
    public ThreadTraceProperties.AllocationMethod method() {
        return ThreadTraceProperties.AllocationMethod.BY_MASS;
    }

    @Override
    public BigDecimal allocationFraction(Lot parentLot, BigDecimal allocatedMass, int allocatedPieces) {
        BigDecimal parentMass = parentLot.getMassKg();
        if (parentMass == null || parentMass.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        if (allocatedMass == null || allocatedMass.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ONE;
        }
        return allocatedMass.divide(parentMass, 6, RoundingMode.HALF_UP).min(BigDecimal.ONE);
    }
}
