package com.yo1no.gramarye.magic.definition.inspection;

import java.util.List;

/**
 * Immutable structural declarations extracted from a Trigger payload.
 *
 * @param providesCurrentTarget whether a successful Trigger occurrence structurally exposes the
 *     {@link TargetSelection#CURRENT_TARGET} slot to its same-node Action; this does not assert
 *     runtime target validity, liveness, range, or reachability
 */
public record TriggerReferenceProjection(
        SourceSelection sourceSelection,
        TargetSelection targetSelection,
        boolean providesCurrentTarget,
        List<NodeReference> references) {
    public TriggerReferenceProjection {
        sourceSelection = InspectionContract.requireNonNull(sourceSelection, "sourceSelection");
        targetSelection = InspectionContract.requireNonNull(targetSelection, "targetSelection");
        references = BoundedNodeReferences.copyOf(references);
    }
}
