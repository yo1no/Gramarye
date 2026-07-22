package com.yo1no.gramarye.magic.definition.validation;

import java.util.Objects;

/** Path-free Trigger and Action reference declarations for one validated node. */
public final class ValidatedNodeReferenceProjection {
    private final ValidatedTriggerReferenceProjection trigger;
    private final ValidatedActionReferenceProjection action;

    ValidatedNodeReferenceProjection(
            ValidatedTriggerReferenceProjection trigger,
            ValidatedActionReferenceProjection action) {
        this.trigger = Objects.requireNonNull(trigger, "trigger");
        this.action = Objects.requireNonNull(action, "action");
    }

    public ValidatedTriggerReferenceProjection trigger() {
        return trigger;
    }

    public ValidatedActionReferenceProjection action() {
        return action;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ValidatedNodeReferenceProjection projection
                        && trigger.equals(projection.trigger)
                        && action.equals(projection.action);
    }

    @Override
    public int hashCode() {
        return Objects.hash(trigger, action);
    }
}
