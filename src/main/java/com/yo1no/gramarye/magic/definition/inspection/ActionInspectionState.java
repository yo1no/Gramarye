package com.yo1no.gramarye.magic.definition.inspection;

/** Action-side inspection state, independent from the Trigger side of the same node. */
public sealed interface ActionInspectionState
        permits ActionInspectionState.NotResolved,
                ActionInspectionState.InspectorMissing,
                ActionInspectionState.Failed,
                ActionInspectionState.Success {
    enum NotResolved implements ActionInspectionState {
        INSTANCE
    }

    enum InspectorMissing implements ActionInspectionState {
        INSTANCE
    }

    record Failed(PayloadInspectionFailure failure) implements ActionInspectionState {
        public Failed {
            failure = InspectionContract.requireNonNull(failure, "failure");
        }
    }

    record Success(ActionReferenceProjection projection) implements ActionInspectionState {
        public Success {
            projection = InspectionContract.requireNonNull(projection, "projection");
        }
    }
}
