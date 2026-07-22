package com.yo1no.gramarye.magic.definition.submission;

import com.yo1no.gramarye.magic.definition.document.AppearanceField;
import com.yo1no.gramarye.magic.validation.ValidationPath;

/** Typed construction of the fixed Draft submission validation paths. */
final class DraftSubmissionPaths {
    private static final ValidationPath ROOT = ValidationPath.empty();
    private static final ValidationPath NODES = ROOT.field("nodes");
    private static final ValidationPath APPEARANCE = ROOT.field("appearance");
    private static final ValidationPath DRAFT_SCHEMA_VERSION =
            ROOT.field("draft_schema_version");

    private DraftSubmissionPaths() {
    }

    static ValidationPath root() {
        return ROOT;
    }

    static ValidationPath draftSchemaVersion() {
        return DRAFT_SCHEMA_VERSION;
    }

    static ValidationPath trigger(int nodeIndex) {
        return node(nodeIndex).field("trigger");
    }

    static ValidationPath action(int nodeIndex) {
        return node(nodeIndex).field("action");
    }

    static ValidationPath appearance() {
        return APPEARANCE;
    }

    static ValidationPath appearanceOverride(int nodeIndex) {
        return node(nodeIndex).field("appearance_override");
    }

    static ValidationPath appearanceField(ValidationPath base, AppearanceField field) {
        return base.field(switch (field) {
            case PRIMARY_ARGB -> "primary_argb";
            case SECONDARY_ARGB -> "secondary_argb";
            case SOUND_PROFILE -> "sound_profile";
            case PARTICLE_PROFILE -> "particle_profile";
            case TRAIL_PROFILE -> "trail_profile";
            case INTENSITY_MILLI -> "intensity_milli";
        });
    }

    private static ValidationPath node(int nodeIndex) {
        return NODES.index(nodeIndex);
    }
}
