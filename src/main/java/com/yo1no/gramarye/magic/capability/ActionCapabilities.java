package com.yo1no.gramarye.magic.capability;

import java.util.Objects;
import java.util.Set;

public record ActionCapabilities(
        SourceRequirement sourceRequirement,
        TargetRequirement targetRequirement,
        boolean allowsSelfTarget,
        Set<ActionOutputKind> outputKinds,
        boolean splittableSource,
        boolean chainableSource,
        boolean repeatableSource,
        boolean modifiesBlocks,
        boolean transfersMana,
        boolean producesPersistentState,
        boolean requiresLiveEntity,
        ControlClass controlClass,
        AppearanceParameterPolicy appearanceParameters) {
    public ActionCapabilities {
        Objects.requireNonNull(sourceRequirement, "sourceRequirement");
        Objects.requireNonNull(targetRequirement, "targetRequirement");
        outputKinds = Set.copyOf(Objects.requireNonNull(outputKinds, "outputKinds"));
        Objects.requireNonNull(controlClass, "controlClass");
        Objects.requireNonNull(appearanceParameters, "appearanceParameters");

        if (targetRequirement == TargetRequirement.NONE && allowsSelfTarget) {
            throw new IllegalArgumentException("allowsSelfTarget requires an optional or required target");
        }
    }
}
