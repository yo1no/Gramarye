package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.capability.ActionOutputKind;
import com.yo1no.gramarye.magic.definition.inspection.ReferenceRole;
import java.util.Objects;
import java.util.Optional;

/** Path-free reference semantics retained after successful validation. */
public final class ValidatedNodeReference {
    private final int referencedNodeIndex;
    private final ReferenceRole role;
    private final Optional<ActionOutputKind> requiredOutputKind;

    ValidatedNodeReference(
            int referencedNodeIndex,
            ReferenceRole role,
            Optional<ActionOutputKind> requiredOutputKind) {
        if (referencedNodeIndex < 0) {
            throw new IllegalArgumentException("referencedNodeIndex must be non-negative");
        }
        this.referencedNodeIndex = referencedNodeIndex;
        this.role = Objects.requireNonNull(role, "role");
        this.requiredOutputKind = Objects.requireNonNull(
                requiredOutputKind, "requiredOutputKind");
    }

    public int referencedNodeIndex() {
        return referencedNodeIndex;
    }

    public ReferenceRole role() {
        return role;
    }

    public Optional<ActionOutputKind> requiredOutputKind() {
        return requiredOutputKind;
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof ValidatedNodeReference reference
                        && referencedNodeIndex == reference.referencedNodeIndex
                        && role == reference.role
                        && requiredOutputKind.equals(reference.requiredOutputKind);
    }

    @Override
    public int hashCode() {
        return Objects.hash(referencedNodeIndex, role, requiredOutputKind);
    }
}
