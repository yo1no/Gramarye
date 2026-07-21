package com.yo1no.gramarye.magic.definition.inspection;

/** Per-side inspection state for one position-derived node index. */
public record NodeReferenceProjection(
        int nodeIndex,
        TriggerInspectionState trigger,
        ActionInspectionState action) {
    public NodeReferenceProjection {
        InspectionContract.require(nodeIndex >= 0, "nodeIndex must be non-negative");
        trigger = InspectionContract.requireNonNull(trigger, "trigger");
        action = InspectionContract.requireNonNull(action, "action");
    }
}
