package com.yo1no.gramarye.magic.definition.inspection;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Immutable structural declarations and outputs extracted from an Action payload. */
public record ActionReferenceProjection(
        SourceSelection sourceSelection,
        TargetSelection targetSelection,
        List<NodeReference> references,
        Set<ActionOutputKind> producedOutputs) {
    public ActionReferenceProjection {
        sourceSelection = InspectionContract.requireNonNull(sourceSelection, "sourceSelection");
        targetSelection = InspectionContract.requireNonNull(targetSelection, "targetSelection");
        references = BoundedNodeReferences.copyOf(references);
        producedOutputs = immutableOutputs(producedOutputs);
    }

    private static Set<ActionOutputKind> immutableOutputs(Set<ActionOutputKind> producedOutputs) {
        InspectionContract.requireNonNull(producedOutputs, "producedOutputs");
        var copy = EnumSet.noneOf(ActionOutputKind.class);
        for (var output : producedOutputs) {
            copy.add(InspectionContract.requireNonNull(output, "producedOutput"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
