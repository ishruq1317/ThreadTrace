package com.threadtrace;

import com.threadtrace.lineage.EventType;
import com.threadtrace.lineage.LotState;
import com.threadtrace.lineage.LotStatus;
import com.threadtrace.shared.DomainExceptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LotStateMachineTest {

    @Test
    void quarantinedLotBlocksDownstreamTransformationsUntilCorrected() {
        LotState quarantined = LotState.of(LotStatus.QUARANTINED);
        assertThrows(DomainExceptions.InvalidLotStateException.class,
                () -> quarantined.assertCanTransform(EventType.SHIP));
        assertThrows(DomainExceptions.InvalidLotStateException.class,
                () -> quarantined.assertCanTransform(EventType.CUT));

        quarantined.assertCanTransform(EventType.CORRECTION);
        assertEquals(LotStatus.IN_PRODUCTION, quarantined.onEvent(EventType.CORRECTION, false));
        assertEquals(LotStatus.QUARANTINED, quarantined.onEvent(EventType.CORRECTION, true));
    }

    @Test
    void completedLotAllowsOnlyCorrectionOrShip() {
        LotState completed = LotState.of(LotStatus.COMPLETED);
        assertThrows(DomainExceptions.InvalidLotStateException.class,
                () -> completed.assertCanTransform(EventType.CUT));
        completed.assertCanTransform(EventType.CORRECTION);
    }

    @Test
    void flaggedStatePersistsUntilNonFlaggedCorrection() {
        LotState flagged = LotState.of(LotStatus.FLAGGED);
        assertEquals(LotStatus.FLAGGED, flagged.onEvent(EventType.SEW, false));
        assertEquals(LotStatus.IN_PRODUCTION, flagged.onEvent(EventType.CORRECTION, false));
    }
}
