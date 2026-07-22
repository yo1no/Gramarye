package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.inspection.SourceSelection;
import com.yo1no.gramarye.magic.definition.inspection.TargetSelection;
import java.util.List;
import java.util.Objects;

/** Path-free Trigger-side structure retained after successful validation. */
public final class ValidatedTriggerReferenceProjection {
    private final SourceSelection sourceSelection;
    private final TargetSelection targetSelection;
    private final boolean providesCurrentTarget;
    private final List<ValidatedNodeReference> references;

    ValidatedTriggerReferenceProjection(
            SourceSelection sourceSelection,
            TargetSelection targetSelection,
            boolean providesCurrentTarget,
            List<ValidatedNodeReference> references) {
        this.sourceSelection = Objects.requireNonNull(sourceSelection, "sourceSelection");
        this.targetSelection = Objects.requireNonNull(targetSelection, "targetSelection");
        this.providesCurrentTarget = providesCurrentTarget;
        this.references = List.copyOf(Objects.requireNonNull(references, "references"));
    }

    public SourceSelection sourceSelection() {
        return sourceSelection;
    }

    public TargetSelection targetSelection() {
        return targetSelection;
    }

    public boolean providesCurrentTarget() {
        return providesCurrentTarget;
    }

    public List<ValidatedNodeReference> references() {
        return references;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ValidatedTriggerReferenceProjection projection
                        && providesCurrentTarget == projection.providesCurrentTarget
                        && sourceSelection.equals(projection.sourceSelection)
                        && targetSelection.equals(projection.targetSelection)
                        && references.equals(projection.references);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSelection, targetSelection, providesCurrentTarget, references);
    }
}
