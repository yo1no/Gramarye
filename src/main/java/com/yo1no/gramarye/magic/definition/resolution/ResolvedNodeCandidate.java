package com.yo1no.gramarye.magic.definition.resolution;

import com.yo1no.gramarye.magic.definition.document.AppearanceOverrideDocument;
import java.util.Objects;

/** One transient node-resolution attempt; nodeIndex is derived from document list position. */
public record ResolvedNodeCandidate(
        int nodeIndex,
        TriggerResolution trigger,
        ActionResolution action,
        AppearanceOverrideDocument appearanceOverride) {
    public ResolvedNodeCandidate {
        if (nodeIndex < 0) {
            throw new IllegalArgumentException("nodeIndex must be non-negative");
        }
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }
}
