package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Strict formal-result framing, taxonomy, counter, and bounded-metadata contract. */
final class P4E0ResearchR2QFormalResultTest {
    private static final String SHA = "11".repeat(32);
    private static final String GIT = "22".repeat(20);
    private static final long XMS = 512L * 1_024L * 1_024L;
    private static final long XMX = 1_536L * 1_024L * 1_024L;

    @Test
    void exactPositiveIsOneCanonicalBoundedLineWithAllTwentyFiveCounters()
            throws Exception {
        var result = exactPositive();
        var line = result.toJsonLine();
        var json = JsonParser.parseString(line).getAsJsonObject();
        var counters = json.getAsJsonObject("counter_values");
        var parsed = P4E0R2QFormalResult.parseLine(line);

        assertAll(
                () -> assertTrue(line.endsWith("\n")),
                () -> assertEquals(1, line.lines().count()),
                () -> assertTrue(line.getBytes(StandardCharsets.UTF_8).length
                        <= P4E0R2QFormalResult.MAXIMUM_JSON_BYTES),
                () -> assertEquals(P4E0ResearchPhaseTypes.R2Q_FORMAL_RESULT_TOP_LEVEL_KEYS,
                        json.keySet()),
                () -> assertEquals(P4E0R2QProfile.COUNTER_COUNT, counters.size()),
                () -> assertEquals(counterSlugs(), counters.keySet()),
                () -> assertEquals(
                        P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                        parsed.processClassification()),
                () -> assertEquals(
                        P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                        parsed.qualificationResult()),
                () -> assertEquals(P4E0R2QProfile.locked().candidateValues(),
                        parsed.observedCounters()),
                () -> assertEquals(XMS, parsed.heap().xms()),
                () -> assertEquals(XMX, parsed.heap().xmx()),
                () -> assertEquals(0L, parsed.dfuInvocations()),
                () -> assertEquals(1_024L, parsed.attachmentAdmissions()),
                () -> assertEquals(65_536L, parsed.rawRootClaims()),
                () -> assertEquals(65_536L, parsed.targetsAudited()),
                () -> assertEquals(0L, parsed.reclaimInvocations()),
                () -> assertFalse(parsed.semanticChecksum().isBlank()),
                () -> assertEquals("", parsed.boundedExceptionClass()),
                () -> assertEquals(line, parsed.toJsonLine()));
    }

    @Test
    void strictParserRejectsDuplicateUnknownMissingWrongTypeTrailingAndOversize()
            throws Exception {
        var canonical = exactPositive().toJsonLine();
        var duplicate = canonical.replaceFirst(
                "\\{", "{\\\"schema_version\\\":0,");
        var unknown = canonical.replaceFirst(
                "\\{", "{\\\"unknown\\\":0,");
        var missing = canonical.replaceFirst(
                "\\\"case_id\\\":\\\"[^\\\"]+\\\",", "");
        var wrongType = canonical.replaceFirst(
                "\\\"case_index\\\":0", "\\\"case_index\\\":\\\"0\\\"");
        var trailing = canonical.substring(0, canonical.length() - 1) + " {}\n";
        var oversized = " ".repeat(P4E0R2QFormalResult.MAXIMUM_JSON_BYTES + 1);

        assertAll(
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(duplicate)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(unknown)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(missing)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(wrongType)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(trailing)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(oversized)),
                () -> assertThrows(IOException.class,
                        () -> P4E0R2QFormalResult.parseLine(
                                canonical.substring(0, canonical.length() - 1))));
    }

    @Test
    void everyCounterRejectionIsCompletedExactMaximumPlusOneWithNoSecondOverrun() {
        var plan = P4E0R2QCasePlan.standard();
        for (var counter : P4E0R2QProfile.Counter.values()) {
            var spec = plan.cases().stream()
                    .filter(candidate -> candidate.targetCounter().equals(Optional.of(counter)))
                    .findFirst().orElseThrow();
            var maximum = P4E0R2QProfile.locked().maximum(counter);
            var counters = P4E0R2QProfile.locked().candidateValues()
                    .with(counter, Math.addExact(maximum, 1L));
            var admissions = counter == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS
                    ? maximum + 1L
                    : counter == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS ? 1_024L : 0L;
            var roots = counter == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                    ? maximum + 1L : 0L;
            var result = result(
                    spec.caseId(), plan.cases().indexOf(spec),
                    P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                    P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER,
                    Optional.of(counter), maximum, maximum + 1L,
                    "COUNTER_CAPACITY_EXCEEDED", "COUNTER_CAPACITY_EXCEEDED",
                    P4E0R2QCasePlan.stageFor(counter).slug(),
                    P4E0R2QCasePlan.stageFor(counter).slug(), true, counters,
                    0L, admissions, roots,
                    0L, 0L, completedHeap(), "");

            assertAll(
                    () -> assertEquals(maximum + 1L, result.observedAtLeast()),
                    () -> assertEquals(maximum + 1L,
                            result.observedCounters().value(counter)),
                    () -> assertEquals(Optional.of(counter), result.targetCounter()),
                    () -> assertEquals(spec.expectedFailure().orElseThrow().code().name(),
                            result.expectedFailureCode()),
                    () -> assertEquals(spec.expectedFailure().orElseThrow().code().name(),
                            result.observedFailureCode()),
                    () -> assertEquals(spec.expectedFailure().orElseThrow().stage().slug(),
                            result.expectedStage()),
                    () -> assertEquals(spec.expectedFailure().orElseThrow().stage().slug(),
                            result.observedStage()),
                    () -> assertTrue(result.allOtherCountersWithinLimit()),
                    () -> assertTrue(EnumSet.allOf(P4E0R2QProfile.Counter.class).stream()
                            .filter(other -> other != counter)
                            .allMatch(other -> result.observedCounters().value(other)
                                    <= P4E0R2QProfile.locked().maximum(other))),
                    () -> assertEquals(
                            P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                            result.processClassification()),
                    () -> assertEquals(
                            P4E0R2QFormalResult.QualificationResult
                                    .REJECTED_EXPECTED_COUNTER,
                            result.qualificationResult()));
            assertAll(
                    () -> assertEquals(admissions, result.attachmentAdmissions()),
                    () -> assertEquals(roots, result.rawRootClaims()),
                    () -> assertEquals(0L, result.targetsAudited()),
                    () -> assertEquals(0L, result.dfuInvocations()),
                    () -> assertEquals(0L, result.reclaimInvocations()));
        }
    }

    @Test
    void counterRejectionCannotClaimMaximumPlusOneWithoutObservingItInTheVector() {
        var spec = P4E0R2QCasePlan.standard().cases().get(1);
        var counter = spec.targetCounter().orElseThrow();
        var failure = spec.expectedFailure().orElseThrow();

        assertThrows(IllegalArgumentException.class, () -> result(
                spec.caseId(), spec.index(),
                P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                P4E0R2QFormalResult.QualificationResult.REJECTED_EXPECTED_COUNTER,
                Optional.of(counter), spec.maximum(), spec.observedAtLeast(),
                failure.code().name(), failure.code().name(),
                failure.stage().slug(), failure.stage().slug(), true,
                P4E0R2QProfile.locked().candidateValues(),
                0L, 0L, 0L, 0L, 0L, completedHeap(), ""));
    }

    @Test
    void positiveAndDataVersionContractsRejectContradictoryCounterFacts() {
        var dataVersion = P4E0R2QCasePlan.standard().cases().get(26);
        var failure = dataVersion.expectedFailure().orElseThrow();
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> result(
                        P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                        P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                        P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                        Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                        false, P4E0R2QProfile.locked().candidateValues(),
                        0L, 1_024L, 65_536L, 65_536L, 0L, completedHeap(), "")),
                () -> assertThrows(IllegalArgumentException.class, () -> result(
                        dataVersion.caseId(), dataVersion.index(),
                        P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                        P4E0R2QFormalResult.QualificationResult
                                .REJECTED_EXPECTED_DATA_VERSION,
                        Optional.empty(), 0L, 0L,
                        failure.code().name(), failure.code().name(),
                        failure.stage().slug(), failure.stage().slug(), false,
                        P4E0R2QProfile.locked().candidateValues(),
                        0L, 0L, 0L, 0L, 0L, completedHeap(), "")));
    }

    @Test
    void dataVersionControlsCarryNoDfuAdmissionRootAuditOrReclaimObservation() {
        var zero = zeroCounters();
        for (var index = 26; index < 29; index++) {
            var spec = P4E0R2QCasePlan.standard().cases().get(index);
            var failure = spec.expectedFailure().orElseThrow();
            var code = failure.code().name();
            var result = result(
                    spec.caseId(), index,
                    P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                    P4E0R2QFormalResult.QualificationResult
                            .REJECTED_EXPECTED_DATA_VERSION,
                    Optional.empty(), 0L, 0L, code, code,
                    "data_version", "data_version", false, zero,
                    0L, 0L, 0L, 0L, 0L, completedHeap(), "");
            assertAll(
                    () -> assertEquals(0L, result.dfuInvocations()),
                    () -> assertEquals(0L, result.attachmentAdmissions()),
                    () -> assertEquals(0L, result.rawRootClaims()),
                    () -> assertEquals(0L, result.targetsAudited()),
                    () -> assertEquals(0L, result.reclaimInvocations()),
                    () -> assertTrue(EnumSet.allOf(P4E0R2QProfile.Counter.class).stream()
                            .allMatch(counter -> result.observedCounters().value(counter) == 0L)));
        }
    }

    @Test
    void processFailuresCannotMasqueradeAsQualificationAndMetadataStaysBounded() {
        for (var classification : P4E0R2QFormalResult.ProcessClassification.values()) {
            if (classification == P4E0R2QFormalResult.ProcessClassification.COMPLETED) {
                continue;
            }
            var result = result(
                    P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                    classification,
                    P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                    Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                    false, P4E0R2QProfile.locked().candidateValues(),
                    0L, 0L, 0L, 0L, 0L,
                    P4E0R2QFormalResult.HeapFacts.unobserved(),
                    "java.lang.IllegalStateException");
            assertEquals(P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                    result.qualificationResult());
        }

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> result(
                        P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                        P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                        P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                        Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                        false, P4E0R2QProfile.locked().candidateValues(),
                        0L, 0L, 0L, 0L, 0L, completedHeap(), "")),
                () -> assertThrows(IllegalArgumentException.class, () -> result(
                        P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                        P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE,
                        P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                        Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                        false, P4E0R2QProfile.locked().candidateValues(),
                        0L, 0L, 0L, 0L, 0L,
                        P4E0R2QFormalResult.HeapFacts.unobserved(),
                        "java.lang.IllegalStateException")),
                () -> assertThrows(IllegalArgumentException.class, () -> result(
                        P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                        P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE,
                        P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                        Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                        false, P4E0R2QProfile.locked().candidateValues(),
                        0L, 0L, 0L, 0L, 0L,
                        P4E0R2QFormalResult.HeapFacts.unobserved(),
                        "/tmp/world: message")));
    }

    @Test
    void semanticChecksumCoversTheBoundedExceptionClass() {
        var failed = result(
                P4E0R2QCasePlan.standard().cases().get(0).caseId(), 0,
                P4E0R2QFormalResult.ProcessClassification.CHILD_EXIT_FAILURE,
                P4E0R2QFormalResult.QualificationResult.NOT_OBSERVED,
                Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                false, zeroCounters(), 0L, 0L, 0L, 0L, 0L,
                P4E0R2QFormalResult.HeapFacts.unobserved(),
                "java.lang.IllegalStateException");
        var mutated = failed.toJsonLine().replace(
                "java.lang.IllegalStateException", "java.lang.RuntimeException");

        assertThrows(IOException.class,
                () -> P4E0R2QFormalResult.parseLine(mutated));
    }

    @Test
    void workloadPreflightFactoryPropagatesIdentityAndEveryStageCutoff() {
        var control = control();
        for (var spec : P4E0R2QCasePlan.standard().cases()) {
            var result = P4E0R2QFormalWorkload.preflightResult(control, spec.index());
            assertAll(
                    () -> assertTrue(result.hasFormalIdentity(control)),
                    () -> assertEquals(spec.caseId(), result.caseId()),
                    () -> assertEquals(spec.index(), result.caseIndex()),
                    () -> assertEquals(
                            P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                            result.processClassification()),
                    () -> assertEquals(0L, result.dfuInvocations()),
                    () -> assertEquals(0L, result.reclaimInvocations()),
                    () -> assertEquals(XMS, result.heap().xms()),
                    () -> assertEquals(XMX, result.heap().xmx()));
            switch (spec.kind()) {
                case POSITIVE -> assertAll(
                        () -> assertEquals(
                                P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                                result.qualificationResult()),
                        () -> assertEquals(1_024L, result.attachmentAdmissions()),
                        () -> assertEquals(65_536L, result.rawRootClaims()),
                        () -> assertEquals(65_536L, result.targetsAudited()),
                        () -> assertEquals(P4E0R2QProfile.locked().candidateValues(),
                                result.observedCounters()));
                case COUNTER_MAX_PLUS_ONE -> {
                    var target = spec.targetCounter().orElseThrow();
                    var failure = spec.expectedFailure().orElseThrow();
                    assertAll(
                            () -> assertEquals(
                                    P4E0R2QFormalResult.QualificationResult
                                            .REJECTED_EXPECTED_COUNTER,
                                    result.qualificationResult()),
                            () -> assertEquals(failure.code().name(),
                                    result.observedFailureCode()),
                            () -> assertEquals(failure.stage().slug(),
                                    result.observedStage()),
                            () -> assertEquals(spec.observedAtLeast(),
                                    result.observedCounters().value(target)),
                            () -> assertEquals(
                                    target == P4E0R2QProfile.Counter.ATTACHMENT_ADMISSIONS
                                            ? 1_025L
                                            : target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                                                    ? 1_024L : 0L,
                                    result.attachmentAdmissions()),
                            () -> assertEquals(
                                    target == P4E0R2QProfile.Counter.RAW_ROOT_CLAIMS
                                            ? 65_537L : 0L,
                                    result.rawRootClaims()),
                            () -> assertEquals(0L, result.targetsAudited()));
                }
                case DATA_VERSION_MISSING, DATA_VERSION_WRONG_TYPE,
                        DATA_VERSION_WRONG_VALUE -> assertAll(
                                () -> assertEquals(
                                        P4E0R2QFormalResult.QualificationResult
                                                .REJECTED_EXPECTED_DATA_VERSION,
                                        result.qualificationResult()),
                                () -> assertEquals(0L, result.attachmentAdmissions()),
                                () -> assertEquals(0L, result.rawRootClaims()),
                                () -> assertEquals(0L, result.targetsAudited()),
                                () -> assertTrue(EnumSet.allOf(
                                                P4E0R2QProfile.Counter.class).stream()
                                        .allMatch(counter -> result.observedCounters()
                                                .value(counter) == 0L)));
            }
        }
    }

    private static P4E0R2QFormalResult exactPositive() {
        var spec = P4E0R2QCasePlan.standard().cases().get(0);
        var counters = P4E0R2QProfile.locked().candidateValues();
        return result(
                spec.caseId(), 0,
                P4E0R2QFormalResult.ProcessClassification.COMPLETED,
                P4E0R2QFormalResult.QualificationResult.ADMITTED_EXACT,
                Optional.empty(), 0L, 0L, "NONE", "NONE", "NONE", "NONE",
                true, counters, 0L, counters.attachmentAdmissions(),
                counters.rawRootClaims(), counters.rawRootClaims(), 0L,
                completedHeap(), "");
    }

    private static P4E0R2QFormalResult result(
            String caseId,
            int caseIndex,
            P4E0R2QFormalResult.ProcessClassification process,
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
            long dfu,
            long admissions,
            long roots,
            long audited,
            long reclaim,
            P4E0R2QFormalResult.HeapFacts heap,
            String exceptionClass) {
        return new P4E0R2QFormalResult(
                SHA, caseId, caseIndex, GIT, GIT, P4E0R2QProfile.manifestHash(),
                P4E0R2QCasePlan.standard().planHash(), SHA,
                P4E0R2QFormalWorkload.expectedCaseFixtureChecksum(
                        P4E0R2QCasePlan.standard().cases().get(caseIndex)),
                P4E0R2QFormalEvidence.formalRunOrderHash(),
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                process, qualification, target, maximum, observedAtLeast,
                expectedCode, observedCode, expectedStage, observedStage,
                allOtherWithin, counters, dfu, admissions, roots, audited, reclaim,
                heap, 1L, exceptionClass);
    }

    private static P4E0R2QFormalResult.HeapFacts completedHeap() {
        return new P4E0R2QFormalResult.HeapFacts(XMS, XMX, XMS, XMS, XMS);
    }

    private static P4E0R2QFormalEvidence.StudyControl control() {
        var fixture = P4E0R2QFormalEvidence.fixtureRootHash();
        var runOrder = P4E0R2QFormalEvidence.formalRunOrderHash();
        var identity = P4E0R2QStudyIdentity.calculateFormal(
                GIT, GIT, P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH, fixture, runOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
        return new P4E0R2QFormalEvidence.StudyControl(
                identity.studyId(), GIT, GIT,
                P4E0R2QFormalEvidence.LOCKED_PROFILE_HASH,
                P4E0R2QFormalEvidence.LOCKED_CASE_PLAN_HASH,
                fixture, runOrder,
                P4E0R2QStudyIdentity.FORMAL_IMPLEMENTATION_SCHEMA_VERSION,
                P4E0R2QFormalEvidence.FORMAL_HEAP_MIB,
                P4E0R2QFormalEvidence.FORMAL_DISK_BUDGET_BYTES);
    }

    private static P4E0R2QProfile.CounterValues zeroCounters() {
        return new P4E0R2QProfile.CounterValues(
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L,
                0L, 0L, 0L, 0L, 0L);
    }

    private static Set<String> counterSlugs() {
        return EnumSet.allOf(P4E0R2QProfile.Counter.class).stream()
                .map(P4E0R2QProfile.Counter::slug)
                .collect(Collectors.toSet());
    }

}
