package com.yo1no.gramarye.magic.definition.document;

import java.util.Objects;

/** One immutable editable draft node; its list position is its only node index. */
public record DraftNode(
        DraftTriggerSlot trigger,
        DraftActionSlot action,
        AppearanceOverrideDocument appearanceOverride) {
    public DraftNode {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(appearanceOverride, "appearanceOverride");
    }
}
