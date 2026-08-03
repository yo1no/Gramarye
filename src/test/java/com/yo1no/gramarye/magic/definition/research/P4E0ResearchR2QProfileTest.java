package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/** Exact profile/plan identity and pre-child negative-preflight gate for P4-E0-R2Q-A. */
final class P4E0ResearchR2QProfileTest {
    @Test
    void lockedProfileHasAllTwentyFiveApprovedTypedCoordinates() {
        var profile = P4E0R2QProfile.locked();
        var values = profile.candidateValues();
        assertAll(
                () -> assertEquals(25, P4E0R2QProfile.Counter.values().length),
                () -> assertEquals(4_096L, values.directoryEntries()),
                () -> assertEquals(2_048L, values.relevantRecords()),
                () -> assertEquals(33_559_514L, values.compressedBytesPerFile()),
                () -> assertEquals(268_435_456L, values.decompressedBytesPerFile()),
                () -> assertEquals(512L, values.containerDepthPerFile()),
                () -> assertEquals(1_024L, values.compoundContainersPerFile()),
                () -> assertEquals(65_537L, values.compoundFieldEntriesPerFile()),
                () -> assertEquals(65_536L, values.listElementsPerFile()),
                () -> assertEquals(268_435_384L, values.byteArrayElementsPerFile()),
                () -> assertEquals(65_536L, values.intArrayElementsPerFile()),
                () -> assertEquals(65_536L, values.longArrayElementsPerFile()),
                () -> assertEquals(67_107_692L, values.modifiedUtf8BytesPerFile()),
                () -> assertEquals(65_537L, values.scalarTagsPerFile()),
                () -> assertEquals(268_440_533L, values.compressedBytesTotal()),
                () -> assertEquals(536_870_912L, values.decompressedBytesTotal()),
                () -> assertEquals(131_072L, values.compoundContainersTotal()),
                () -> assertEquals(524_288L, values.compoundFieldEntriesTotal()),
                () -> assertEquals(131_072L, values.listElementsTotal()),
                () -> assertEquals(456_524_705L, values.byteArrayElementsTotal()),
                () -> assertEquals(131_072L, values.intArrayElementsTotal()),
                () -> assertEquals(131_072L, values.longArrayElementsTotal()),
                () -> assertEquals(75_497_472L, values.modifiedUtf8BytesTotal()),
                () -> assertEquals(458_752L, values.scalarTagsTotal()),
                () -> assertEquals(1_024L, values.attachmentAdmissions()),
                () -> assertEquals(65_536L, values.rawRootClaims()),
                () -> assertEquals(3_955, profile.acceptedDataVersion()),
                () -> assertEquals(0, profile.maxDfuRecords()),
                () -> assertEquals(1_536, profile.qualificationHeapMiB()),
                () -> assertEquals(
                        P4E0R2QProfile.OverrunPolicy.INCOMPLETE_AND_CONTINUE,
                        profile.overrunPolicy()),
                () -> assertEquals(12_884_901_888L, profile.researchDiskBudgetBytes()));
    }

    @Test
    void constructorLocksRelationshipsAndTheApprovedTuple() {
        var profile = P4E0R2QProfile.locked();
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> profile.candidateValues().with(
                                P4E0R2QProfile.Counter.DIRECTORY_ENTRIES, -1L)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0R2QProfile(
                                profile.candidateValues().with(
                                        P4E0R2QProfile.Counter.DIRECTORY_ENTRIES, 4_097L),
                                profile.acceptedDataVersion(),
                                profile.maxDfuRecords(),
                                profile.qualificationHeapMiB(),
                                profile.overrunPolicy(),
                                profile.researchDiskBudgetBytes())),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0R2QProfile(
                                profile.candidateValues(),
                                profile.acceptedDataVersion(),
                                1,
                                profile.qualificationHeapMiB(),
                                profile.overrunPolicy(),
                                profile.researchDiskBudgetBytes())));
    }

    @Test
    void coordinateManifestHasExactIdentityCoordinatesAndPrecedence() {
        var profile = P4E0R2QProfile.locked();
        var manifest = P4E0R2QProfile.manifestJson();
        var expectedSlugs = EnumSet.allOf(P4E0R2QProfile.Counter.class).stream()
                .map(P4E0R2QProfile.Counter::slug)
                .collect(java.util.stream.Collectors.toSet());
        var actualPrecedence = new java.util.ArrayList<String>();
        for (var value : manifest.getAsJsonArray("failure_precedence")) {
            actualPrecedence.add(value.getAsString());
        }
        var expectedPrecedence = java.util.Arrays.stream(
                        P4E0R2QCasePlan.FailureStage.values())
                .map(P4E0R2QCasePlan.FailureStage::slug)
                .toList();

        assertAll(
                () -> assertEquals(0, manifest.get("schema_version").getAsInt()),
                () -> assertEquals(
                        P4E0R2QProfile.PROFILE_NAME,
                        manifest.get("profile_name").getAsString()),
                () -> assertEquals(
                        expectedSlugs,
                        manifest.getAsJsonObject("counter_coordinates").keySet()),
                () -> assertEquals(expectedPrecedence, actualPrecedence),
                () -> assertEquals(
                        profile.researchDiskBudgetBytes(),
                        manifest.getAsJsonObject("disk_budget").get("bytes").getAsLong()),
                () -> assertTrue(P4E0R2QProfile.manifestText().endsWith("\n")),
                () -> assertTrue(P4E0R2QProfile.manifestHash().matches("[0-9a-f]{64}")),
                () -> assertEquals(
                        P4E0R2QProfile.manifestHash(),
                        P4E0ResearchHashing.sha256(P4E0R2QProfile.manifestText())));
        for (var slug : expectedSlugs) {
            var coordinate = manifest.getAsJsonObject("counter_coordinates")
                    .getAsJsonObject(slug);
            assertAll(
                    () -> assertFalse(coordinate.get("measure").getAsString().isBlank()),
                    () -> assertFalse(coordinate.get("checkpoint").getAsString().isBlank()));
        }
    }

    @Test
    void approvedStructuralArithmeticIsExact() {
        assertAll(
                () -> assertEquals(
                        536_870_912L,
                        Math.addExact(382_335_895L, 154_535_017L)),
                () -> assertEquals(
                        456_524_705L,
                        Math.addExact(301_989_688L, 154_535_017L)));
    }

    @Test
    void immutableCasePlanHasExactlyOnePositiveTwentyFiveCountersAndThreeDataVersions() {
        var plan = P4E0R2QCasePlan.standard();
        var cases = plan.cases();
        var ids = cases.stream().map(P4E0R2QCasePlan.CaseSpec::caseId).toList();
        var counterTargets = cases.stream()
                .flatMap(spec -> spec.targetCounter().stream())
                .collect(java.util.stream.Collectors.toSet());
        var dataVersions = cases.stream()
                .filter(spec -> spec.kind().name().startsWith("DATA_VERSION_"))
                .toList();

        assertAll(
                () -> assertEquals(29, cases.size()),
                () -> assertEquals(29, new HashSet<>(ids).size()),
                () -> assertEquals(
                        "p4-e0-r2q-balanced-v0-1536-exact", ids.getFirst()),
                () -> assertEquals(
                        EnumSet.allOf(P4E0R2QProfile.Counter.class), counterTargets),
                () -> assertEquals(3, dataVersions.size()),
                () -> assertTrue(cases.stream()
                        .allMatch(spec -> spec.expectedDfuInvocations() == 0)),
                () -> assertTrue(cases.stream()
                        .filter(spec -> spec.kind() != P4E0R2QCasePlan.CaseKind.POSITIVE)
                        .allMatch(spec -> spec.expectedFailure().isPresent())),
                () -> assertFalse(plan.canonicalJson().contains("1280")),
                () -> assertFalse(plan.canonicalJson().contains("DFU")),
                () -> assertTrue(plan.planHash().matches("[0-9a-f]{64}")),
                () -> assertEquals(
                        plan.planHash(), P4E0ResearchHashing.sha256(plan.canonicalJson())),
                () -> assertThrows(
                        UnsupportedOperationException.class,
                        () -> cases.add(cases.getFirst())));
    }

    @Test
    void everyCounterNegativePassesSingleMaximumPlusOnePreflight() {
        var profile = P4E0R2QProfile.locked();
        var plan = P4E0R2QCasePlan.standard();
        var positive = P4E0R2QFixturePlan.locked().counters();
        for (var spec : plan.cases()) {
            if (spec.kind() != P4E0R2QCasePlan.CaseKind.COUNTER_MAX_PLUS_ONE) {
                continue;
            }
            var target = spec.targetCounter().orElseThrow();
            var fixture = P4E0R2QFixturePlan.negativeFixture(spec);
            var preflight = plan.preflightNegative(spec, fixture);
            assertAll(
                    () -> assertEquals(target, preflight.targetCounter()),
                    () -> assertEquals(
                            positive.value(target), fixture.proof().sourceValue()),
                    () -> assertEquals(profile.maximum(target), preflight.maximum()),
                    () -> assertEquals(
                            Math.addExact(profile.maximum(target), 1L),
                            preflight.observedAtLeast()),
                    () -> assertEquals(
                            P4E0R2QCasePlan.stageFor(target),
                            preflight.firstFailureStage()),
                    () -> assertTrue(preflight.allOtherCountersWithinLimit()),
                    () -> assertEquals(
                            P4E0R2QFixturePlan.recipeFor(target).proofKind(),
                            preflight.physicalProofKind()));
            for (var other : P4E0R2QProfile.Counter.values()) {
                if (other != target) {
                    assertEquals(
                            positive.value(other),
                            fixture.observedCounters().value(other),
                            () -> "unexpected second mutation: " + other);
                    assertTrue(
                            fixture.observedCounters().value(other) <= profile.maximum(other),
                            () -> "second overrun: " + other);
                }
            }
        }
    }

    @Test
    void negativePreflightRejectsMismatchedTypedPhysicalProof() {
        var plan = P4E0R2QCasePlan.standard();
        var directoryCase = plan.cases().stream()
                .filter(spec -> spec.targetCounter().orElse(null)
                        == P4E0R2QProfile.Counter.DIRECTORY_ENTRIES)
                .findFirst()
                .orElseThrow();
        var relevantCase = plan.cases().stream()
                .filter(spec -> spec.targetCounter().orElse(null)
                        == P4E0R2QProfile.Counter.RELEVANT_RECORDS)
                .findFirst()
                .orElseThrow();
        var relevantFixture = P4E0R2QFixturePlan.negativeFixture(relevantCase);
        assertThrows(
                IllegalArgumentException.class,
                () -> plan.preflightNegative(directoryCase, relevantFixture));
    }

    @Test
    void physicalMutationProofCannotSelfAttestTheWrongRecipeOrDelta() {
        var target = P4E0R2QProfile.Counter.DIRECTORY_ENTRIES;
        var recipe = P4E0R2QFixturePlan.recipeFor(target);
        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0R2QFixturePlan.PhysicalMutationProof(
                                target,
                                P4E0R2QCasePlan.MutationKind.ADD_SELECTED_PRIMARY_RECORD,
                                recipe.proofKind(),
                                recipe.coupledCounters(),
                                4_096L,
                                4_097L)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0R2QFixturePlan.PhysicalMutationProof(
                                target,
                                recipe.mutationKind(),
                                recipe.proofKind(),
                                recipe.coupledCounters(),
                                4_096L,
                                4_098L)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0R2QFixturePlan.PhysicalMutationProof(
                                target,
                                recipe.mutationKind(),
                                P4E0R2QFixturePlan.PhysicalProofKind.TYPED_ROOT_PROJECTION,
                                recipe.coupledCounters(),
                                4_096L,
                        4_097L)));
    }

    @Test
    void allThreeDataVersionControlsHaveTypedPhysicalProofAndZeroDfu() {
        var plan = P4E0R2QCasePlan.standard();
        var controls = plan.cases().stream()
                .filter(spec -> spec.kind().name().startsWith("DATA_VERSION_"))
                .toList();
        assertEquals(3, controls.size());
        for (var spec : controls) {
            var fixture = P4E0R2QFixturePlan.dataVersionFixture(spec);
            var preflight = plan.preflightDataVersion(spec, fixture);
            assertAll(
                    () -> assertEquals(spec.kind(), preflight.caseKind()),
                    () -> assertEquals(
                            spec.expectedFailure().orElseThrow().code(),
                            preflight.failureCode()),
                    () -> assertEquals(
                            P4E0R2QCasePlan.FailureStage.DATA_VERSION,
                            preflight.firstFailureStage()),
                    () -> assertEquals(fixture.proofKind(), preflight.physicalProofKind()),
                    () -> assertEquals(fixture.resultingState(), preflight.resultingState()),
                    () -> assertEquals(3_955, fixture.sourceValue()),
                    () -> assertEquals(0, fixture.expectedDfuInvocations()),
                    () -> assertEquals(0, preflight.expectedDfuInvocations()));
        }
        assertAll(
                () -> assertEquals(
                        P4E0R2QFixturePlan.DataVersionTagState.MISSING,
                        P4E0R2QFixturePlan.dataVersionFixture(controls.get(0)).resultingState()),
                () -> assertEquals(
                        P4E0R2QFixturePlan.DataVersionTagState.STRING_TAG,
                        P4E0R2QFixturePlan.dataVersionFixture(controls.get(1)).resultingState()),
                () -> assertEquals(
                        P4E0R2QFixturePlan.DataVersionTagState.INT_TAG_WRONG_VALUE,
                        P4E0R2QFixturePlan.dataVersionFixture(controls.get(2)).resultingState()),
                () -> assertEquals(
                        3_954,
                        P4E0R2QFixturePlan.dataVersionFixture(controls.get(2))
                                .resultingIntValue()));
    }

    @Test
    void rawRootMutationUsesItsSeparateTypedProjectionEnvelope() {
        var plan = P4E0R2QCasePlan.standard();
        var rootCase = plan.cases().stream()
                .filter(spec -> spec.targetCounter().orElse(null)
                        == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS)
                .findFirst()
                .orElseThrow();
        var fixture = P4E0R2QFixturePlan.negativeFixture(rootCase);

        assertAll(
                () -> assertEquals(65_536L, fixture.proof().sourceValue()),
                () -> assertEquals(65_537L, fixture.proof().observedValue()),
                () -> assertEquals(
                        P4E0R2QFixturePlan.PhysicalProofKind.TYPED_ROOT_PROJECTION,
                        fixture.proof().proofKind()),
                () -> assertTrue(fixture.proof().coupledCounters().isEmpty()),
                () -> assertEquals(
                        P4E0R2QFixturePlan.locked().counters().decompressedBytesTotal(),
                        fixture.observedCounters().decompressedBytesTotal()),
                () -> assertEquals(
                        P4E0R2QFixturePlan.locked().counters().scalarTagsTotal(),
                        fixture.observedCounters().scalarTagsTotal()));
    }

    @Test
    void precedenceSelectsPerFileAndEarlierStructuralCheckpointsFirst() {
        var profile = P4E0R2QProfile.locked();
        var bothCompressed = profile.candidateValues()
                .with(P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE, 33_559_515L)
                .with(P4E0R2QProfile.Counter.COMPRESSED_BYTES_TOTAL, 268_440_534L);
        var fieldAndUtf = profile.candidateValues()
                .with(P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE, 65_538L)
                .with(P4E0R2QProfile.Counter.MODIFIED_UTF8_BYTES_PER_FILE, 67_107_693L);
        assertAll(
                () -> assertEquals(
                        P4E0R2QProfile.Counter.COMPRESSED_BYTES_PER_FILE,
                        P4E0R2QCasePlan.firstExceeded(bothCompressed).orElseThrow()),
                () -> assertEquals(
                        P4E0R2QProfile.Counter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                        P4E0R2QCasePlan.firstExceeded(fieldAndUtf).orElseThrow()),
                () -> assertNotEquals(
                        P4E0R2QProfile.manifestHash(),
                        P4E0R2QCasePlan.standard().planHash()));
    }
}
