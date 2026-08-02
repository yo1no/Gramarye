package com.yo1no.gramarye.magic.definition.research;

/**
 * Closed, research-only run scenarios. Each scenario executes the complete smoke taxonomy while
 * selecting a materially different Ready Attachment coordinate.
 */
enum P4E0ResearchScenario {
    CORRECTNESS_SMOKE(true),
    PLAYERDATA_COORDINATE_SMOKE(false);

    private final boolean includeExistingMixedDraft;

    P4E0ResearchScenario(boolean includeExistingMixedDraft) {
        this.includeExistingMixedDraft = includeExistingMixedDraft;
    }

    boolean includeExistingMixedDraft() {
        return includeExistingMixedDraft;
    }
}
