package com.yo1no.gramarye.magic.definition.inspection;

/** Trigger-side inspection state, independent from the Action side of the same node. */
public sealed interface TriggerInspectionState
        permits TriggerInspectionState.NotResolved,
                TriggerInspectionState.InspectorMissing,
                TriggerInspectionState.Failed,
                TriggerInspectionState.Success {
    enum NotResolved implements TriggerInspectionState {
        INSTANCE
    }

    enum InspectorMissing implements TriggerInspectionState {
        INSTANCE
    }

    record Failed(PayloadInspectionFailure failure) implements TriggerInspectionState {
        public Failed {
            failure = InspectionContract.requireNonNull(failure, "failure");
        }
    }

    record Success(TriggerReferenceProjection projection) implements TriggerInspectionState {
        public Success {
            projection = InspectionContract.requireNonNull(projection, "projection");
        }
    }
}
