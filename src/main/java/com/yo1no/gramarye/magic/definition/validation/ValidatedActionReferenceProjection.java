package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Path-free Action-side structure retained after successful validation. */
public final class ValidatedActionReferenceProjection {
    private final SourceSelection sourceSelection;
    private final TargetSelection targetSelection;
    private final List<ValidatedNodeReference> references;
    private final Set<ActionOutputKind> producedOutputs;

    ValidatedActionReferenceProjection(
            SourceSelection sourceSelection,
            TargetSelection targetSelection,
            List<ValidatedNodeReference> references,
            Set<ActionOutputKind> producedOutputs) {
        this.sourceSelection = Objects.requireNonNull(sourceSelection, "sourceSelection");
        this.targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        this.references = List.copyOf(Objects.requireNonNull(references, "references"));
        this.producedOutputs = immutableOutputs(producedOutputs);
    }

    public SourceSelection sourceSelection() {
        return sourceSelection;
    }

    public TargetSelection targetSelection() {
        return targetSelection;
    }

    public List<ValidatedNodeReference> references() {
        return references;
    }

    public Set<ActionOutputKind> producedOutputs() {
        return producedOutputs;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ValidatedActionReferenceProjection projection
                        && sourceSelection.equals(projection.sourceSelection)
                        && targetSelection.equals(projection.targetSelection)
                        && references.equals(projection.references)
                        && producedOutputs.equals(projection.producedOutputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSelection, targetSelection, references, producedOutputs);
    }

    private static Set<ActionOutputKind> immutableOutputs(Set<ActionOutputKind> outputs) {
        Objects.requireNonNull(outputs, "producedOutputs");
        var copy = EnumSet.noneOf(ActionOutputKind.class);
        for (var output : outputs) {
            copy.add(Objects.requireNonNull(output, "producedOutput"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
