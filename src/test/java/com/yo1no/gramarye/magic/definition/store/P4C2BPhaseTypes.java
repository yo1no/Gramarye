package com.yo1no.gramarye.magic.definition.store;

import java.util.Set;

/** Exact test-only P4-C2-B source allowlist. No name in this class is production-authorized. */
final class P4C2BPhaseTypes {
    static final String PLAYER_PACKAGE_PATH =
            "com/yo1no/gramarye/magic/definition/player";
    static final String STORE_PACKAGE_PATH =
            "com/yo1no/gramarye/magic/definition/store";

    static final Set<String> PROBE_SOURCE_FILE_NAMES = Set.of(
            "P4C2FileVerifier.java",
            "P4C2FixtureBuilder.java",
            "P4C2FixtureManifest.java",
            "P4C2Hashing.java",
            "P4C2ProbeCase.java",
            "P4C2ProbeMain.java",
            "P4C2ProbeSummary.java",
            "P4C2RunMode.java");

    static final Set<String> GAME_TEST_SOURCE_FILE_NAMES = Set.of(
            "P4C2MemoryGameTests.java",
            "P4C2ProbeServerLifecycle.java");

    static final Set<String> STORE_PROBE_SOURCE_FILE_NAMES = Set.of(
            "P4C2StoreProbe.java");

    private P4C2BPhaseTypes() {
    }

    static boolean containsSourceFileName(String name) {
        return PROBE_SOURCE_FILE_NAMES.contains(name)
                || GAME_TEST_SOURCE_FILE_NAMES.contains(name)
                || STORE_PROBE_SOURCE_FILE_NAMES.contains(name);
    }

    static boolean containsTopLevelName(String name) {
        return containsSourceFileName(name + ".java");
    }
}
