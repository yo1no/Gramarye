package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;

/** Bounded count-only report for one completed Store reclaim scan. */
public record SkillReclaimReport(
        int historiesScanned,
        int revisionsScanned,
        int historiesChanged,
        int revisionsReclaimed) {
    public SkillReclaimReport {
        if (historiesScanned < 0
                || revisionsScanned < 0
                || historiesChanged < 0
                || revisionsReclaimed < 0) {
            throw new IllegalArgumentException("reclaim report counts must be non-negative");
        }
        if (historiesScanned > MagicSafetyCeilings.MAX_COMMITTED_SKILLS_GLOBAL) {
            throw new IllegalArgumentException("historiesScanned exceeds the Store hard ceiling");
        }
        if (revisionsScanned > MagicSafetyCeilings.MAX_RETAINED_REVISIONS_GLOBAL) {
            throw new IllegalArgumentException("revisionsScanned exceeds the Store hard ceiling");
        }
        if (revisionsScanned < historiesScanned) {
            throw new IllegalArgumentException(
                    "each scanned history must contain at least one revision");
        }
        if ((historiesScanned == 0) != (revisionsScanned == 0)) {
            throw new IllegalArgumentException(
                    "zero scanned histories and revisions must occur together");
        }
        if (historiesChanged > historiesScanned) {
            throw new IllegalArgumentException(
                    "historiesChanged cannot exceed historiesScanned");
        }
        if (revisionsReclaimed > revisionsScanned - historiesScanned) {
            throw new IllegalArgumentException(
                    "reclaim cannot remove the implicit latest revision of a history");
        }
        if (historiesChanged > revisionsReclaimed) {
            throw new IllegalArgumentException(
                    "each changed history must reclaim at least one revision");
        }
        if ((historiesChanged == 0) != (revisionsReclaimed == 0)) {
            throw new IllegalArgumentException(
                    "zero changed histories and reclaimed revisions must occur together");
        }
    }
}
