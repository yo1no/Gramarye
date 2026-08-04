package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** All-case aggregation, atomic publication, and archive-isolation contract. */
final class P4E0ResearchR2QFormalEvidenceTest {
    private static final String GIT_HEAD = "33".repeat(20);
    private static final String GIT_TREE = "44".repeat(20);
    private static final String FIXTURE_ROOT = P4E0R2QFormalEvidence.fixtureRootHash();
    private static final long XMS = 512L * 1_024L * 1_024L;
    private static final long XMX = 1_536L * 1_024L * 1_024L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void successfulSetIsExactlyTwentyNineOrderedUniqueCompletedResults()
            throws Exception {
        var control = control();
        var results = successfulResults(control);

        P4E0R2QFormalEvidence.requireSuccessfulSet(results);

        assertAll(
                () -> assertEquals(29, results.size()),
                () -> assertEquals(29, results.stream()
                        .map(P4E0R2QFormalResult::caseId).distinct().count()),
                () -> assertEquals(
                        java.util.stream.IntStream.range(0, 29).boxed().toList(),
                        results.stream().map(P4E0R2QFormalResult::caseIndex).toList()),
                () -> assertEquals(1L, count(results,
                        P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT)),
                () -> assertEquals(25L, count(results,
                        P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_COUNTER)),
                () -> assertEquals(3L, count(results,
                        P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_DATA_VERSION)),
                () -> assertEquals(0L, count(results,
                        P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED)),
                () -> assertTrue(results.stream().allMatch(result ->
                        result.processClassification()
                                == P4E0R2QFormalResult.ProcessClassification.COMPLETED)),
                () -> assertTrue(results.stream().allMatch(result ->
                        result.hasFormalIdentity(control))));
    }

    @Test
    void studyControlRejectsSelfConsistentButNonCompiledFixtureOrRunOrder() {
        var runOrder = P4E0R2QFormalEvidence.formalRunOrderHash();
        var wrongFixture = "77".repeat(32);
        var wrongRunOrder = "88".repeat(32);
        var wrongFixtureIdentity = P4E0R2QStudyIdentity.calculateFormal(
                GIT_HEAD, GIT_TREE, P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH, wrongFixture, runOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
        var wrongRunIdentity = P4E0R2QStudyIdentity.calculateFormal(
                GIT_HEAD, GIT_TREE, P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH, FIXTURE_ROOT, wrongRunOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new P4E0R2QFormalEvidence.StudyControl(
                                wrongFixtureIdentity.studyId(), GIT_HEAD, GIT_TREE,
                                P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH,
                                wrongFixture, runOrder,
                                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new P4E0R2QFormalEvidence.StudyControl(
                                wrongRunIdentity.studyId(), GIT_HEAD, GIT_TREE,
                                P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH,
                                FIXTURE_ROOT, wrongRunOrder,
                                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES)));
    }

    @Test
    void partialDuplicateOutOfOrderAndProcessFailureSetsAreRejected() throws Exception {
        var control = control();
        var successful = successfulResults(control);
        var partial = successful.subList(0, 28);
        var duplicate = new ArrayList<>(successful);
        duplicate.set(1, successful.get(0));
        var outOfOrder = new ArrayList<>(successful);
        java.util.Collections.swap(outOfOrder, 1, 2);
        var failed = new ArrayList<>(successful);
        failed.set(0, processFailure(control, 0));

        assertAll(
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalEvidence.requireSuccessfulSet(partial)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalEvidence.requireSuccessfulSet(duplicate)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalEvidence.requireSuccessfulSet(outOfOrder)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalEvidence.requireSuccessfulSet(failed)));
    }

    @Test
    void publicationIsOneAtomicDirectoryMoveAndFailurePublishesNothing()
            throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("work");
        var official = temporaryDirectory.resolve("reports/p4-e0-r2q");
        writeVerifiedResults(work, successfulResults(control));
        var invocations = new int[1];

        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.aggregateAndPublish(
                work, official, control, (source, target) -> {
                    invocations[0]++;
                    assertEquals(official, target);
                    assertEquals(P4E0R2QFormalEvidence.OFFICIAL_FILES,
                            regularNames(source));
                    throw new IOException("injected directory move failure");
                }));

        assertAll(
                () -> assertEquals(1, invocations[0]),
                () -> assertFalse(Files.exists(official)),
                () -> assertEquals(Set.of("work", "reports"),
                        regularOrDirectoryNames(temporaryDirectory)),
                () -> assertTrue(Files.isDirectory(work)));
    }

    @Test
    void publishedArtifactsAreExactlySixCanonicalIdentityBoundFiles() throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("work");
        var official = temporaryDirectory.resolve("reports/p4-e0-r2q");
        var results = successfulResults(control);
        writeVerifiedResults(work, results);

        P4E0R2QFormalEvidence.aggregateAndPublish(
                work, official, control, P4E0R2QFormalEvidence.atomicDirectoryMover());
        P4E0R2QFormalEvidence.verifyOfficial(official, control);

        var runs = Files.readAllLines(official.resolve("runs.jsonl"));
        var summary = Files.readString(official.resolve("summary.md"));
        var provenance = Files.readString(official.resolve("PROVENANCE.txt"));
        assertAll(
                () -> assertEquals(P4E0R2QFormalEvidence.OFFICIAL_FILES,
                        regularNames(official)),
                () -> assertEquals(29, runs.size()),
                () -> assertEquals(results.stream()
                                .map(result -> result.toJsonLine().stripTrailing()).toList(),
                        runs),
                () -> assertEquals(P4E0R2QProfile.manifestJson() + "\n",
                        Files.readString(official.resolve("r2q-profile.json"))),
                () -> assertEquals(P4E0R2QCasePlan.standard().canonicalJson() + "\n",
                        Files.readString(official.resolve("r2q-case-plan.json"))),
                () -> assertTrue(summary.contains("EXPLORATORY_NON_NORMATIVE")),
                () -> assertTrue(summary.contains("process COMPLETED: 29")),
                () -> assertTrue(summary.contains("REJECTED_EXPECTED_COUNTER: 25")),
                () -> assertTrue(summary.contains("REJECTED_EXPECTED_DATA_VERSION: 3")),
                () -> assertTrue(provenance.contains("study_id=" + control.studyId())),
                () -> assertTrue(provenance.contains("git_head=" + control.gitHead())),
                () -> assertEquals(5, Files.readAllLines(
                        official.resolve("SHA256SUMS.txt")).size()));
    }

    @Test
    void officialRunsAreRejectedByABoundedWholeArtifactRead() throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("bounded-work");
        var official = temporaryDirectory.resolve("bounded-official");
        writeVerifiedResults(work, successfulResults(control));
        P4E0R2QFormalEvidence.aggregateAndPublish(
                work, official, control, P4E0R2QFormalEvidence.atomicDirectoryMover());

        Files.writeString(
                official.resolve(P4E0R2QFormalEvidence.RUNS_FILE),
                "x".repeat(Math.multiplyExact(
                        P4E0R2QFormalResult.MAXIMUM_JSON_BYTES,
                        P4E0R2QCasePlan.CASE_COUNT) + 1));

        assertThrows(IOException.class,
                () -> P4E0R2QFormalEvidence.verifyOfficial(official, control));
    }

    @Test
    void staleArchivePreservesValidatedOfficialAndFailsClosedOnCollision()
            throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("work");
        var official = temporaryDirectory.resolve("reports/p4-e0-r2q");
        writeVerifiedResults(work, successfulResults(control));
        P4E0R2QFormalEvidence.aggregateAndPublish(
                work, official, control, P4E0R2QFormalEvidence.atomicDirectoryMover());
        var originalRunsHash = P4E0ResearchHashing.sha256(
                official.resolve(P4E0R2QFormalEvidence.RUNS_FILE));
        var originalChecksumsHash = P4E0ResearchHashing.sha256(
                official.resolve(P4E0R2QFormalEvidence.CHECKSUMS_FILE));

        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.archiveStaleOfficial(
                official, temporaryDirectory.resolve("wrong-stale"), "77".repeat(32)));
        assertTrue(Files.isDirectory(official),
                "archive identity must be parsed and validated before any move");

        var archived = P4E0R2QFormalEvidence.archiveStaleOfficial(
                official, temporaryDirectory.resolve("stale-evidence"), control.studyId());
        var archiveChecksums = Files.readString(archived.resolve("SHA256SUMS.txt"));

        assertAll(
                () -> assertFalse(Files.exists(official)),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("STALE_PROVENANCE.txt"))),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("OFFICIAL_SHA256SUMS.txt"))),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("SHA256SUMS.txt"))),
                () -> assertEquals(originalRunsHash, P4E0ResearchHashing.sha256(
                        archived.resolve(P4E0R2QFormalEvidence.RUNS_FILE))),
                () -> assertEquals(originalChecksumsHash, P4E0ResearchHashing.sha256(
                        archived.resolve("OFFICIAL_SHA256SUMS.txt"))),
                () -> assertTrue(archiveChecksums.contains("  STALE_PROVENANCE.txt\n")),
                () -> assertTrue(archiveChecksums.contains("  OFFICIAL_SHA256SUMS.txt\n")));

        var secondWork = temporaryDirectory.resolve("work-two");
        writeVerifiedResults(secondWork, successfulResults(control));
        P4E0R2QFormalEvidence.aggregateAndPublish(
                secondWork, official, control, P4E0R2QFormalEvidence.atomicDirectoryMover());
        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.archiveStaleOfficial(
                official, temporaryDirectory.resolve("stale-evidence"), control.studyId()));
        assertTrue(Files.isDirectory(official));
    }

    @Test
    void malformedOrMixedOfficialEvidenceCannotBeArchivedAsValidatedStaleEvidence()
            throws Exception {
        var control = control();
        var malformed = temporaryDirectory.resolve("malformed");
        Files.createDirectories(malformed);
        Files.writeString(malformed.resolve("runs.jsonl"), "{}\n");

        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.archiveStaleOfficial(
                malformed, temporaryDirectory.resolve("stale"), control.studyId()));
        assertTrue(Files.isDirectory(malformed));

        var work = temporaryDirectory.resolve("mixed-work");
        var official = temporaryDirectory.resolve("mixed-official");
        writeVerifiedResults(work, successfulResults(control));
        P4E0R2QFormalEvidence.aggregateAndPublish(
                work, official, control, P4E0R2QFormalEvidence.atomicDirectoryMover());
        Files.writeString(official.resolve("PROVENANCE.txt"),
                Files.readString(official.resolve("PROVENANCE.txt"))
                        .replace(control.gitHead(), "66".repeat(20)));
        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.archiveStaleOfficial(
                official, temporaryDirectory.resolve("stale-two"), control.studyId()));
        assertTrue(Files.isDirectory(official));
    }

    @Test
    void failedEvidenceKeepsOnlyBoundedAllowlistedFactsAndCannotBeReused()
            throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("formal-work");
        var failedRoot = temporaryDirectory.resolve("failed-evidence");
        var official = temporaryDirectory.resolve("reports/p4-e0-r2q");
        var r2 = temporaryDirectory.resolve("reports/p4-e0-research/runs.jsonl");
        Files.createDirectories(r2.getParent());
        Files.writeString(r2, "R2-EVIDENCE\n");
        var r2Hash = P4E0ResearchHashing.sha256(r2);
        P4E0R2QFormalEvidence.writeControl(work.resolve("study-control.json"), control);
        P4E0R2QFormalEvidence.writeResult(
                P4E0R2QFormalEvidence.caseDirectory(work, 0)
                        .resolve("verified-result.json"),
                successfulResult(control, P4E0R2QCasePlan.standard().cases().get(0)));
        P4E0R2QFormalEvidence.writeResult(
                P4E0R2QFormalEvidence.caseDirectory(work, 1)
                        .resolve("child-result.json"),
                processFailure(control, 1,
                        P4E0R2QFormalResult.ProcessClassification.OOME_EXIT));
        var failingCase = P4E0R2QFormalEvidence.caseDirectory(work, 1);
        Files.writeString(failingCase.resolve("case-manifest.json"),
                "{\"schema_version\":0,\"case_index\":1}\n");
        P4E0R2QFormalEvidence.writeForcedMarker(
                failingCase.resolve("exit-code.txt"), "137\n");
        P4E0R2QFormalEvidence.writeForcedMarker(
                failingCase.resolve("parent-deadline.marker"),
                "deadline_epoch_millis=1700000000000\n");
        var raw = failingCase.resolve("game/world/raw-fixture.dat");
        Files.createDirectories(raw.getParent());
        Files.write(raw, new byte[131_072]);

        var archived = P4E0R2QFormalEvidence.preserveFailed(
                work, failedRoot, control, "OOME_EXIT");
        var checksums = Files.readString(archived.resolve("SHA256SUMS.txt"));

        assertAll(
                () -> assertFalse(Files.exists(work)),
                () -> assertFalse(Files.exists(official)),
                () -> assertEquals(r2Hash, P4E0ResearchHashing.sha256(r2)),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("study-control.json"))),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("cases/00/verified-result.json"))),
                () -> assertTrue(Files.isRegularFile(
                        archived.resolve("cases/01/case-manifest.status"))),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.OOME_EXIT,
                        P4E0R2QFormalEvidence.readResult(
                                archived.resolve("cases/01/child-result.json"))
                                .processClassification()),
                () -> assertTrue(Files.readString(archived.resolve("FAILURE.txt"))
                        .contains("code=OOME_EXIT\n")),
                () -> assertTrue(checksums.contains("  FAILURE.txt\n")),
                () -> assertTrue(checksums.contains(
                        "  cases/00/verified-result.json\n")),
                () -> assertTrue(checksums.contains(
                        "  cases/01/case-manifest.status\n")),
                () -> assertTrue(checksums.contains(
                        "  cases/01/parent-deadline.marker\n")),
                () -> assertFalse(containsFileNamed(archived, "raw-fixture.dat")),
                () -> assertFalse(containsDirectoryNamed(archived, "game")));

        var retryWork = temporaryDirectory.resolve("formal-work-retry");
        P4E0R2QFormalEvidence.writeControl(
                retryWork.resolve("study-control.json"), control);
        assertThrows(IOException.class, () -> P4E0R2QFormalEvidence.preserveFailed(
                retryWork, failedRoot, control, "CHILD_EXIT_FAILURE"));
        assertTrue(Files.isDirectory(retryWork),
                "a collision must not delete new work or reuse the study archive");
    }

    @Test
    void failedEvidenceRebuildsCanonicalControlWithoutCopyingOversizeInput() throws Exception {
        var control = control();
        var work = temporaryDirectory.resolve("failed-copy-work");
        var failedRoot = temporaryDirectory.resolve("failed-copy-root");
        Files.createDirectories(work);
        Files.writeString(
                work.resolve("study-control.json"),
                "x".repeat(P4E0R2QFormalEvidence.MAXIMUM_CONTROL_BYTES + 1));

        var archived = P4E0R2QFormalEvidence.preserveFailed(
                work, failedRoot, control, "INSTRUMENTATION_FAILURE");

        assertAll(
                () -> assertFalse(Files.exists(work)),
                () -> assertEquals(
                        control,
                        P4E0R2QFormalEvidence.readControl(
                                archived.resolve("study-control.json"))),
                () -> assertTrue(Files.size(archived.resolve("study-control.json"))
                        <= P4E0R2QFormalEvidence.MAXIMUM_CONTROL_BYTES),
                () -> assertFalse(Files.exists(
                        failedRoot.resolve("." + control.studyId() + ".staging"))));
    }

    private static long count(
            List<P4E0R2QFormalResult> results,
            P4E0R2QFormalResult.QualificationResult qualification) {
        return results.stream().filter(result -> result.qualificationResult() == qualification)
                .count();
    }

    private static P4E0R2QFormalEvidence.StudyControl control() {
        var runOrder = P4E0R2QFormalEvidence.formalRunOrderHash();
        var identity = P4E0R2QStudyIdentity.calculateFormal(
                GIT_HEAD, GIT_TREE, P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH, FIXTURE_ROOT, runOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
        return new P4E0R2QFormalEvidence.StudyControl(
                identity.studyId(), GIT_HEAD, GIT_TREE,
                P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH,
                FIXTURE_ROOT, runOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
    }

    private static List<P4E0R2QFormalResult> successfulResults(
            P4E0R2QFormalEvidence.StudyControl control) {
        var results = new ArrayList<P4E0R2QFormalResult>();
        for (var spec : P4E0R2QCasePlan.standard().cases()) {
            results.add(successfulResult(control, spec));
        }
        return List.copyOf(results);
    }

    private static P4E0R2QFormalResult successfulResult(
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec) {
        var heap = new P4E0R2QFormalResult.HeapFacts(XMS, XMX, XMS, XMS, XMS);
        return switch (spec.kind()) {
            case POSITIVE -> result(
                    control, spec,
                    P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                    Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                    true, P4E0R2QProfile.locked().candidateValues(),
                    1_024L, 65_536L, 65_536L, heap);
            case COUNTER_MAX_PLUS_ONE -> {
                var target = spec.targetCounter().orElseThrow();
                var failure = spec.expectedFailure().orElseThrow();
                var admissions = target == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS
                        ? spec.observedAtLeast()
                        : target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS ? 1_024L : 0L;
                var roots = target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                        ? spec.observedAtLeast() : 0L;
                yield result(
                        control, spec,
                        P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_COUNTER,
                        Optional.of(target), spec.maximum(), spec.observedAtLeast(),
                        failure.code().name(), failure.code().name(),
                        failure.stage().slug(), failure.stage().slug(), true,
                        P4E0R2QProfile.locked().candidateValues()
                                .with(target, spec.observedAtLeast()),
                        admissions, roots, 0L, heap);
            }
            case DATA_VERSION_MISSING, DATA_VERSION_WRONG_TYPE,
                    DATA_VERSION_WRONG_VALUE -> {
                var failure = spec.expectedFailure().orElseThrow();
                yield result(
                        control, spec,
                        P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_DATA_VERSION,
                        Optional.empty(), 0L, 0L, failure.code().name(),
                        failure.code().name(), failure.stage().slug(),
                        failure.stage().slug(), false, zeroCounters(),
                        0L, 0L, 0L, heap);
            }
        };
    }

    private static P4E0R2QFormalResult processFailure(
            P4E0R2QFormalEvidence.StudyControl control, int index) {
        return processFailure(control, index,
                P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE);
    }

    private static P4E0R2QFormalResult processFailure(
            P4E0R2QFormalEvidence.StudyControl control,
            int index,
            P4E0R2QFormalResult.ProcessClassification classification) {
        var spec = P4E0R2QCasePlan.standard().cases().get(index);
        return new P4E0R2QFormalResult(
                control.studyId(), spec.caseId(), index, control.gitHead(), control.gitTree(),
                control.profileHash(), control.casePlanHash(), control.fixtureRootHash(),
                P4E0R2QFormalWorkload.expectedCaseFixtureChecksum(spec),
                control.runOrderHash(), control.implementationSchemaVersion(),
                classification,
                P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE", false,
                zeroCounters(), 0L, 0L, 0L, 0L, 0L,
                P4E0R2QFormalResult.HeapFacts.unobserved(), 1L,
                "java.lang.IllegalStateException");
    }

    private static P4E0R2QFormalResult result(
            P4E0R2QFormalEvidence.StudyControl control,
            P4E0R2QCasePlan.CaseSpec spec,
            P4E0R2QFormalResult.QualificationResult qualification,
            Optional<P4E0R2QProfile.Counter> target,
            long maximum,
            long observedAtLeast,
            String expectedCode,
            String observedCode,
            String expectedStage,
            String observedStage,
            boolean allOtherWithin,
            P4E0R2QProfile.CounterValues counters,
            long admissions,
            long roots,
            long audited,
            P4E0R2QFormalResult.HeapFacts heap) {
        return new P4E0R2QFormalResult(
                control.studyId(), spec.caseId(), spec.index(), control.gitHead(),
                control.gitTree(), control.profileHash(), control.casePlanHash(),
                control.fixtureRootHash(),
                P4E0R2QFormalWorkload.expectedCaseFixtureChecksum(spec),
                control.runOrderHash(),
                control.implementationSchemaVersion(),
                P4E0R2QFormalResult.ProcessClassification.COMPLETED, qualification,
                target, maximum, observedAtLeast, expectedCode, observedCode,
                expectedStage, observedStage, allOtherWithin, counters,
                0L, admissions, roots, audited, 0L, heap, 1L, "");
    }

    private static void writeVerifiedResults(
            Path work, List<P4E0R2QFormalResult> results) throws IOException {
        for (var result : results) {
            var caseRoot = P4E0R2QFormalEvidence.caseDirectory(work, result.caseIndex());
            P4E0R2QFormalWorkload.writePreflightManifest(
                    caseRoot, controlFor(result), result.caseIndex());
            var path = caseRoot.resolve("verified-result.json");
            P4E0R2QFormalEvidence.writeResult(path, result);
        }
    }

    private static P4E0R2QFormalEvidence.StudyControl controlFor(
            P4E0R2QFormalResult result) {
        return new P4E0R2QFormalEvidence.StudyControl(
                result.studyId(), result.gitHead(), result.gitTree(), result.profileHash(),
                result.casePlanHash(), result.fixtureHash(), result.runOrderHash(),
                result.implementationSchemaVersion(),
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
    }

    private static Set<String> regularNames(Path root) throws IOException {
        try (var entries = Files.list(root)) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static Set<String> regularOrDirectoryNames(Path root) throws IOException {
        try (var entries = Files.list(root)) {
            return entries.map(path -> path.getFileName().toString())
                    .collect(java.util.stream.Collectors.toSet());
        }
    }

    private static boolean containsFileNamed(Path root, String name) throws IOException {
        try (var entries = Files.walk(root)) {
            return entries.anyMatch(path -> Files.isRegularFile(path)
                    && path.getFileName().toString().equals(name));
        }
    }

    private static boolean containsDirectoryNamed(Path root, String name) throws IOException {
        try (var entries = Files.walk(root)) {
            return entries.anyMatch(path -> Files.isDirectory(path)
                    && path.getFileName().toString().equals(name));
        }
    }

    private static P4E0R2QProfile.CounterValues zeroCounters() {
        return new P4E0R2QProfile.CounterValues(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L);
    }
}
