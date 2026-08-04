package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Fixed vectors for the future formal R2Q provenance identity; no artifacts are published. */
final class P4E0ResearchR2QStudyIdentityTest {
    private static final String HEAD = "0123456789abcdef0123456789abcdef01234567";
    private static final String TREE = "89abcdef0123456789abcdef0123456789abcdef";
    private static final String PROFILE = "11".repeat(32);
    private static final String CASE_PLAN = "22".repeat(32);
    private static final String FIXTURE_ROOT = "33".repeat(32);
    private static final String RUN_ORDER = "44".repeat(32);

    @Test
    void fixedVectorLocksCanonicalSchemaAndStudyId() {
        var identity = identity(FIXTURE_ROOT, 0);
        var expectedPayload = "{\"schema_version\":0,"
                + "\"git_head\":\"" + HEAD + "\","
                + "\"git_tree\":\"" + TREE + "\","
                + "\"profile_manifest_sha256\":\"" + PROFILE + "\","
                + "\"case_plan_sha256\":\"" + CASE_PLAN + "\","
                + "\"fixture_root_sha256\":\"" + FIXTURE_ROOT + "\","
                + "\"research_implementation_schema_version\":0}";
        var expectedStudyId =
                "0507a803ed0d14b17d57ace67eb5d8c94e8576032d13731cffe1d06360b3ee40";

        assertAll(
                () -> assertEquals(0, P4E0R2QStudyIdentity.SCHEMA_VERSION),
                () -> assertEquals(
                        0, P4E0R2QStudyIdentity.CURRENT_IMPLEMENTATION_SCHEMA_VERSION),
                () -> assertEquals(expectedPayload, identity.canonicalPayload()),
                () -> assertEquals(expectedStudyId, identity.studyId()),
                () -> assertEquals(
                        expectedPayload.substring(0, expectedPayload.length() - 1)
                                + ",\"study_id_sha256\":\"" + expectedStudyId + "\"}",
                        identity.canonicalJson()),
                () -> assertEquals(HEAD, identity.gitHead()),
                () -> assertEquals(TREE, identity.gitTree()),
                () -> assertEquals(PROFILE, identity.profileManifestHash()),
                () -> assertEquals(CASE_PLAN, identity.casePlanHash()),
                () -> assertEquals(FIXTURE_ROOT, identity.fixtureRootHash()),
                () -> assertEquals(0, identity.researchImplementationSchemaVersion()));
    }

    @Test
    void everyFormalCoordinateParticipatesInTheHash() {
        var baseline = identity(FIXTURE_ROOT, 0).studyId();
        assertAll(
                () -> assertNotEquals(baseline, P4E0R2QStudyIdentity.calculate(
                        "1123456789abcdef0123456789abcdef01234567",
                        TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT, 0).studyId()),
                () -> assertNotEquals(baseline, P4E0R2QStudyIdentity.calculate(
                        HEAD,
                        "99abcdef0123456789abcdef0123456789abcdef",
                        PROFILE, CASE_PLAN, FIXTURE_ROOT, 0).studyId()),
                () -> assertNotEquals(baseline, P4E0R2QStudyIdentity.calculate(
                        HEAD, TREE, "44".repeat(32), CASE_PLAN, FIXTURE_ROOT, 0).studyId()),
                () -> assertNotEquals(baseline, P4E0R2QStudyIdentity.calculate(
                        HEAD, TREE, PROFILE, "44".repeat(32), FIXTURE_ROOT, 0).studyId()),
                () -> assertNotEquals(baseline, identity("44".repeat(32), 0).studyId()),
                () -> assertNotEquals(baseline, identity(FIXTURE_ROOT, 1).studyId()));
    }

    @Test
    void malformedOrAmbiguousCoordinatesAreRejected() {
        assertAll(
                () -> assertThrows(NullPointerException.class, () ->
                        P4E0R2QStudyIdentity.calculate(
                                null, TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT, 0)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        P4E0R2QStudyIdentity.calculate(
                                HEAD.toUpperCase(), TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT, 0)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        P4E0R2QStudyIdentity.calculate(
                                HEAD, TREE, PROFILE.substring(1), CASE_PLAN, FIXTURE_ROOT, 0)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        identity(FIXTURE_ROOT, -1)));
    }

    @Test
    void formalVectorBindsRunOrderHeapBudgetAndImplementation() {
        var identity = formalIdentity(RUN_ORDER);
        var expectedPayload = "{\"schema_version\":1,"
                + "\"git_head\":\"" + HEAD + "\","
                + "\"git_tree\":\"" + TREE + "\","
                + "\"profile_manifest_sha256\":\"" + PROFILE + "\","
                + "\"case_plan_sha256\":\"" + CASE_PLAN + "\","
                + "\"fixture_root_sha256\":\"" + FIXTURE_ROOT + "\","
                + "\"research_implementation_schema_version\":1,"
                + "\"formal_run_order_sha256\":\"" + RUN_ORDER + "\","
                + "\"qualification_heap_mib\":1536,"
                + "\"research_disk_budget_bytes\":12884901888}";
        var expectedStudyId =
                "a8314b1701df39c06e35b81cb833e4ccf2b38a9c2897183dae0536493dbb1d7d";

        assertAll(
                () -> assertEquals(1, P4E0R2QStudyIdentity.FORMAL_SCHEMA_VERSION),
                () -> assertEquals(
                        1, P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION),
                () -> assertEquals(expectedPayload, identity.canonicalPayload()),
                () -> assertEquals(expectedStudyId, identity.studyId()),
                () -> assertEquals(RUN_ORDER, identity.formalRunOrderHash()),
                () -> assertEquals(1_536, identity.qualificationHeapMiB()),
                () -> assertEquals(12_884_901_888L, identity.researchDiskBudgetBytes()),
                () -> assertEquals(1, identity.researchImplementationSchemaVersion()),
                () -> assertEquals(true, identity.formal()));
    }

    @Test
    void everyFormalOnlyCoordinateParticipatesAndCannotDrift() {
        var baseline = formalIdentity(RUN_ORDER).studyId();
        assertAll(
                () -> assertNotEquals(baseline, formalIdentity("55".repeat(32)).studyId()),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        P4E0R2QStudyIdentity.calculateFormal(
                                HEAD, TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT,
                                RUN_ORDER, 0, 1_536, 12_884_901_888L)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        P4E0R2QStudyIdentity.calculateFormal(
                                HEAD, TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT,
                                RUN_ORDER, 1, 1_535, 12_884_901_888L)),
                () -> assertThrows(IllegalArgumentException.class, () ->
                        P4E0R2QStudyIdentity.calculateFormal(
                                HEAD, TREE, PROFILE, CASE_PLAN, FIXTURE_ROOT,
                                RUN_ORDER, 1, 1_536, 12_884_901_887L)));
    }

    private static P4E0R2QStudyIdentity identity(
            String fixtureRoot, int implementationSchemaVersion) {
        return P4E0R2QStudyIdentity.calculate(
                HEAD,
                TREE,
                PROFILE,
                CASE_PLAN,
                fixtureRoot,
                implementationSchemaVersion);
    }

    private static P4E0R2QStudyIdentity formalIdentity(String runOrder) {
        return P4E0R2QStudyIdentity.calculateFormal(
                HEAD,
                TREE,
                PROFILE,
                CASE_PLAN,
                FIXTURE_ROOT,
                runOrder,
                1,
                1_536,
                12_884_901_888L);
    }
}
