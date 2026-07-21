package com.yo1no.gramarye.magic.definition.inspection;

import java.util.List;

/** Immutable structural declarations extracted from a Trigger payload. */
public record TriggerReferenceProjection(
        SourceSelection sourceSelection,
        TargetSelection targetSelection,
        List<NodeReference> references) {
    public TriggerReferenceProjection {
        sourceSelection = InspectionContract.requireNonNull(sourceSelection, "sourceSelection");
        targetSelection = InspectionContract.requireNonNull(targetSelection, "targetSelection");
        references = BoundedNodeReferences.copyOf(references);
    }
}
