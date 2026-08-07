package com.yo1no.gramarye.magic.definition.store;

import java.util.Objects;

/**
 * The sole P4-E1-A gate from the effective heap observation to allocation of an audit budget.
 * Source work remains owned by later callers and can start only from {@link Qualified}.
 */
final class P4E1SourceAdmissionPreflight {
    private P4E1SourceAdmissionPreflight() {
    }

    static Result evaluate() {
        return evaluate(P4E1HeapFloorObservation.observe());
    }

    static Result evaluate(P4E1HeapFloorObservation.Probe probe) {
        return evaluate(P4E1HeapFloorObservation.observe(
                Objects.requireNonNull(probe, "probe")));
    }

    static Result evaluate(P4E1HeapFloorObservation observation) {
        Objects.requireNonNull(observation, "observation");
        return switch (observation.status()) {
            case QUALIFIED_FLOOR_PRESENT -> new Qualified(
                    observation,
                    new P4E1AuditBudget(new QualifiedPermit()));
            case HEAP_FLOOR_NOT_MET -> new Incomplete(
                    observation,
                    P4E1SourceFailure.heapFloor(
                            P4E1SourceFailure.Code.HEAP_FLOOR_NOT_MET, ""));
            case HEAP_FLOOR_UNVERIFIABLE -> new Incomplete(
                    observation,
                    P4E1SourceFailure.heapFloor(
                            P4E1SourceFailure.Code.HEAP_FLOOR_UNVERIFIABLE,
                            observation.exceptionClassName()));
        };
    }

    sealed interface Result permits Qualified, Incomplete {
        P4E1HeapFloorObservation observation();
    }

    record Qualified(
            P4E1HeapFloorObservation observation,
            P4E1AuditBudget budget) implements Result {
        Qualified {
            Objects.requireNonNull(observation, "observation");
            Objects.requireNonNull(budget, "budget");
            if (observation.status()
                    != P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT) {
                throw new IllegalArgumentException("qualified preflight needs qualified heap");
            }
        }
    }

    record Incomplete(
            P4E1HeapFloorObservation observation,
            P4E1SourceFailure failure) implements Result {
        Incomplete {
            Objects.requireNonNull(observation, "observation");
            Objects.requireNonNull(failure, "failure");
            if (observation.status()
                    == P4E1HeapFloorStatus.QUALIFIED_FLOOR_PRESENT) {
                throw new IllegalArgumentException("incomplete preflight needs nonqualified heap");
            }
        }
    }

    static final class QualifiedPermit {
        private QualifiedPermit() {
        }
    }
}
