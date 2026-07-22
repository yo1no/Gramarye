package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.action.ResolvedActionDefinition;
import com.yo1no.gramarye.magic.definition.trigger.ResolvedTriggerDefinition;
import java.util.Objects;

/** One provenance-free node in a validated, non-persistent definition projection. */
public final class ValidatedNodeDefinition {
    private final int nodeIndex;
    private final ResolvedTriggerDefinition<?> trigger;
    private final ResolvedActionDefinition<?> action;
    private final ValidatedNodeReferenceProjection references;
    private final RuntimeNeutralAppearanceOverride appearanceOverride;

    ValidatedNodeDefinition(
            int nodeIndex,
            ResolvedTriggerDefinition<?> trigger,
            ResolvedActionDefinition<?> action,
            ValidatedNodeReferenceProjection references,
            RuntimeNeutralAppearanceOverride appearanceOverride) {
        if (nodeIndex < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
        this.nodeIndex = nodeIndex;
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.action = Objects.requireNonNull(action, "action");
        this.references = Objects.requireNonNull(references, "references");
        this.appearanceOverride = Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }

    public int nodeIndex() {
        return nodeIndex;
    }

    public ResolvedTriggerDefinition<?> trigger() {
        return trigger;
    }

    public ResolvedActionDefinition<?> action() {
        return action;
    }

    public ValidatedNodeReferenceProjection references() {
        return references;
    }

    public RuntimeNeutralAppearanceOverride appearanceOverride() {
        return appearanceOverride;
    }
}
