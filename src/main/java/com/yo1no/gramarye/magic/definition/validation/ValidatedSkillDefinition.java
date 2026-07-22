package com.yo1no.gramarye.magic.definition.validation;

import com.yo1no.gramarye.magic.definition.document.SkillReference;
import java.util.List;
import java.util.Objects;

/** Immutable, non-persistent definition admitted through the P3-B3 validation gate. */
public final class ValidatedSkillDefinition {
    private final SkillReference reference;
    private final List<ValidatedNodeDefinition> nodes;
    private final RuntimeNeutralAppearance appearance;

    ValidatedSkillDefinition(
            SkillReference reference,
            List<ValidatedNodeDefinition> nodes,
            RuntimeNeutralAppearance appearance) {
        this.reference = Objects.requireNonNull(reference, "reference");
        this.nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        if (this.nodes.isEmpty()) {
            throw new IllegalArgumentException("validated definition must contain at least one node");
        }
        for (var index = 0; index < this.nodes.size(); index++) {
            if (this.nodes.get(index).nodeIndex() != index) {
                throw new IllegalArgumentException(
                        "validated nodeIndex must equal its list position");
            }
        }
        this.appearance = Objects.requireNonNull(appearance, "appearance");
    }

    public SkillReference reference() {
        return reference;
    }

    public List<ValidatedNodeDefinition> nodes() {
        return nodes;
    }

    public RuntimeNeutralAppearance appearance() {
        return appearance;
    }
}
