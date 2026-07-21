package com.yo1no.gramarye.magic.definition.inspection;

/** Target actually selected by a typed payload; distinct from descriptor TargetRequirement. */
public enum TargetSelection {
    NONE,
    SELF,
    CURRENT_TARGET,
    PRIOR_OUTPUT
}
