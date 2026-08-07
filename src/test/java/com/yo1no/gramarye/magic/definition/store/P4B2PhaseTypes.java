package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/**
 * Exact P4-B2 production allowlist shared by its API gate and legacy P3-D gates.
 * P4-B2-B types are deliberately absent because both B2-B source sets are test-only.
 */
final class P4B2PhaseTypes {
    static final Set<String> TOP_LEVEL_TYPE_NAMES = Set.of(
            "BoundedChannelInputStream",
            "BoundedDecompressedInputStream",
            "ControlledSkillPin",
            "GramaryeSkillSavedData",
            "GzipFailureRecorder",
            "GzipHeaderVerifier",
            "PrimaryFileMetadata",
            "SkillDefinitionStoreService",
            "SkillSavedDataLifecycleGameTests",
            "SkillSavedDataPrimaryFailure",
            "SkillSavedDataPrimaryIngress",
            "SkillSavedDataPrimaryLoadResult",
            "SkillSavedDataRuntimeFailure",
            "SkillSavedDataState",
            "SkillSubsystemLifecycleException",
            "SkillSubsystemResult",
            "SkillSubsystemUnavailableReason",
            "StrictSingleMemberGzipCore",
            "StrictSingleMemberGzipInput",
            "StrictSingleMemberGzipResult");

    private P4B2PhaseTypes() {
    }

    static boolean containsTopLevelName(String name) {
        return TOP_LEVEL_TYPE_NAMES.contains(name);
    }

    static boolean containsSourceFileName(String name) {
        if (!name.endsWith(".java")) {
            return false;
        }
        return containsTopLevelName(name.substring(0, name.length() - ".java".length()));
    }
}
