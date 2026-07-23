package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

/** Canonical technical capacity dimensions shared by Store restore and commit admission. */
public enum SkillStoreCapacityScope {
    OWNER_SKILL_HISTORIES,
    GLOBAL_SKILL_HISTORIES,
    SKILL_RETAINED_REVISIONS,
    GLOBAL_RETAINED_REVISIONS;

    int canonicalMaximum() {
        return switch (this) {
            case OWNER_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_PER_OWNER;
            case GLOBAL_SKILL_HISTORIES ->
                    MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL;
            case SKILL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_PER_SKILL;
            case GLOBAL_RETAINED_REVISIONS ->
                    MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL;
        };
    }
}
