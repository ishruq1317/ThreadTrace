package com.threadtrace.lineage;

import com.threadtrace.shared.DomainExceptions;

/**
 * State Pattern for Lot lifecycle (FR2, FR4).
 * Prevents downstream shipping/packing on quarantined lots while allowing corrections and auditor flags.
 */
public interface LotState {

    LotStatus status();

    default void assertCanTransform(EventType eventType) {
        if (eventType == EventType.CORRECTION) {
            return;
        }
    }

    default LotStatus onEvent(EventType eventType, boolean flagged) {
        if (flagged) {
            return LotStatus.FLAGGED;
        }
        return switch (eventType) {
            case RECEIVE, SPLIT, MERGE -> LotStatus.CREATED;
            case CUT, SEW, WASH_OR_DYE, FINISH, PACK -> LotStatus.IN_PRODUCTION;
            case SHIP -> LotStatus.COMPLETED;
            case CORRECTION -> status();
        };
    }

    static LotState of(LotStatus status) {
        return switch (status) {
            case CREATED -> CreatedState.INSTANCE;
            case IN_PRODUCTION -> InProductionState.INSTANCE;
            case COMPLETED -> CompletedState.INSTANCE;
            case FLAGGED -> FlaggedState.INSTANCE;
            case QUARANTINED -> QuarantinedState.INSTANCE;
        };
    }

    final class CreatedState implements LotState {
        static final CreatedState INSTANCE = new CreatedState();

        @Override
        public LotStatus status() {
            return LotStatus.CREATED;
        }
    }

    final class InProductionState implements LotState {
        static final InProductionState INSTANCE = new InProductionState();

        @Override
        public LotStatus status() {
            return LotStatus.IN_PRODUCTION;
        }
    }

    final class CompletedState implements LotState {
        static final CompletedState INSTANCE = new CompletedState();

        @Override
        public LotStatus status() {
            return LotStatus.COMPLETED;
        }

        @Override
        public void assertCanTransform(EventType eventType) {
            if (eventType != EventType.CORRECTION && eventType != EventType.SHIP) {
                throw new DomainExceptions.InvalidLotStateException(
                        "Cannot execute " + eventType + " on a COMPLETED lot; record a CORRECTION event instead");
            }
        }
    }

    final class FlaggedState implements LotState {
        static final FlaggedState INSTANCE = new FlaggedState();

        @Override
        public LotStatus status() {
            return LotStatus.FLAGGED;
        }

        @Override
        public LotStatus onEvent(EventType eventType, boolean flagged) {
            if (eventType == EventType.CORRECTION && !flagged) {
                return LotStatus.IN_PRODUCTION;
            }
            return LotStatus.FLAGGED;
        }
    }

    final class QuarantinedState implements LotState {
        static final QuarantinedState INSTANCE = new QuarantinedState();

        @Override
        public LotStatus status() {
            return LotStatus.QUARANTINED;
        }

        @Override
        public void assertCanTransform(EventType eventType) {
            if (eventType != EventType.CORRECTION) {
                throw new DomainExceptions.InvalidLotStateException(
                        "Lot is QUARANTINED and blocked from " + eventType + "; record a CORRECTION event to clear");
            }
        }

        @Override
        public LotStatus onEvent(EventType eventType, boolean flagged) {
            if (eventType == EventType.CORRECTION && !flagged) {
                return LotStatus.IN_PRODUCTION;
            }
            return LotStatus.QUARANTINED;
        }
    }
}
