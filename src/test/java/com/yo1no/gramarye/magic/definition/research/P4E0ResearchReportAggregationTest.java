package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Focused strict-schema, frontier, and publication tests for P4-E0-R2 reports. */
final class P4E0ResearchReportAggregationTest {
    private static final String GIT_HEAD = "1".repeat(40);
    private static final String STUDY_ID = "2".repeat(64);

    @Test
    void exactFiveHeapGridAndPlanIdentityAreDeterministic() {
        var plan = fixturePlan();
        var replay = fixturePlan();
        assertAll(
                () -> assertEquals(
                        List.of(1024, 1280, 1536, 1792, 2048),
                        P4E0ResearchMatrixPlan.HEAP_GRID_MIB),
                () -> assertEquals(5, plan.runCount()),
                () -> assertEquals(plan.canonicalJson(), replay.canonicalJson()),
                () -> assertEquals(plan.planHash(), replay.planHash()),
                () -> assertEquals(
                        P4E0ResearchMatrixPlan.DISCLAIMER,
                        "Observed pass/fail frontiers are machine-, fixture- and "
                                + "implementation-specific evidence. They do not become "
                                + "Gramarye authority until explicitly approved in P4-E0-B."),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> new P4E0ResearchMatrixPlan(List.of(
                                runSpec(1, 64, "fixture-0")))));
    }

    @Test
    void runJsonRejectsDuplicateUnknownTrailingWrongTypeAndOverflow() throws Exception {
        var plan = fixturePlan();
        var record = fixtureRecords(plan).getFirst();
        var json = record.toJsonLine();
        assertAll(
                () -> assertEquals(
                        record.spec(), P4E0ResearchRunRecord.parseLine(json).spec()),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.R2_RUN_TOP_LEVEL_KEYS,
                        JsonParser.parseString(json).getAsJsonObject().keySet()));

        var duplicateTop = json.replaceFirst(
                "\\{\\\"schema_version\\\":0,",
                "{\"schema_version\":0,\"schema_version\":0,");
        var duplicateNested = json.replaceFirst(
                "\\\"exit_code\\\":0,",
                "\"exit_code\":0,\"exit_code\":0,");
        var unknownTop = json.replaceFirst("\\{", "{\"unexpected\":0,");
        var unknownNested = json.replaceFirst(
                "\\\"process_result\\\":\\{",
                "\"process_result\":{\"unexpected\":0,");
        var wrongType = json.replaceFirst(
                "\\\"heap_mib\\\":1024", "\"heap_mib\":\"1024\"");
        var overflow = json.replaceFirst(
                "\\\"elapsed_millis\\\":100",
                "\"elapsed_millis\":9223372036854775808");

        for (var malformed : List.of(
                duplicateTop,
                duplicateNested,
                unknownTop,
                unknownNested,
                wrongType,
                overflow,
                json + "{}",
                json + "\n{}")) {
            assertThrows(IOException.class, () -> P4E0ResearchRunRecord.parseLine(malformed));
        }
    }

    @Test
    void fixtureManifestIsStrictBoundedCanonicalAndDetectsSkippedPoints()
            throws Exception {
        var plan = fixturePlan();
        var manifest = fixtureManifest(plan);
        var json = manifest.toBoundedJson();
        var parsed = P4E0ResearchFixtureManifest.parse(json);
        assertAll(
                () -> assertEquals(plan.runCount(), parsed.plannedRuns()),
                () -> assertEquals(plan.planHash(), parsed.planHash()),
                () -> assertEquals(List.of("conditional-skipped"), parsed.skippedPoints()),
                () -> assertEquals(json, parsed.toBoundedJson()));

        var duplicate = json.replaceFirst(
                "\\{\\\"schema_version\\\":0,",
                "{\"schema_version\":0,\"schema_version\":0,");
        var unknown = json.replaceFirst("\\{", "{\"unexpected\":0,");
        var overflow = json.replaceFirst(
                "\\\"disk_budget_bytes\\\":34359738368",
                "\"disk_budget_bytes\":9223372036854775808");
        for (var malformed : List.of(duplicate, unknown, overflow, json + "{}")) {
            assertThrows(
                    IOException.class,
                    () -> P4E0ResearchFixtureManifest.parse(malformed));
        }
    }

    @Test
    void aggregatePublishesOneCsvRowPerJsonRunAndObservedFrontiersOnly()
            throws Exception {
        var plan = fixturePlan();
        var manifest = fixtureManifest(plan);
        var records = fixtureRecords(plan);
        var root = projectRoot().resolve(
                "build/reports/p4-e0-research/report-aggregation-unit");
        deleteTree(root);
        try {
            P4E0ResearchReportAggregator.writeStudy(root, plan, manifest, records);
            P4E0ResearchReportAggregator.verifyPublished(root, plan);

            var jsonRecords = P4E0ResearchReportAggregator.readJsonl(
                    root.resolve(P4E0ResearchReportAggregator.RUNS_JSONL),
                    plan.runCount());
            var csvLines = Files.readAllLines(
                    root.resolve(P4E0ResearchReportAggregator.FRONTIERS_CSV));
            var summary = Files.readString(
                    root.resolve(P4E0ResearchReportAggregator.SUMMARY_MARKDOWN));
            Set<String> artifactNames;
            try (var paths = Files.list(root)) {
                artifactNames = paths.map(path -> path.getFileName().toString())
                        .collect(java.util.stream.Collectors.toSet());
            }
            var frontiers = P4E0ResearchReportAggregator.frontiers(records);
            var directoryKey = P4E0ResearchReportAggregator.FrontierKey.of(
                    plan.requireRun(0));
            var directory = frontiers.get(directoryKey);

            assertAll(
                    () -> assertEquals(records.size(), jsonRecords.size()),
                    () -> assertEquals(records.size() + 1, csvLines.size()),
                    () -> assertEquals(
                            P4E0ResearchReportAggregator.CSV_HEADER,
                            csvLines.getFirst()),
                    () -> assertTrue(csvLines.stream().allMatch(line ->
                            line.split(",", -1).length
                                    == P4E0ResearchReportAggregator.CSV_HEADER
                                            .split(",", -1).length)),
                    () -> assertEquals(
                            P4E0ResearchPhaseTypes.R2_REPORT_ARTIFACT_NAMES,
                            artifactNames),
                    () -> assertEquals(256L, directory.largestObservedCompleted()),
                    () -> assertEquals(1024L, directory.smallestObservedFailed()),
                    () -> assertEquals(1024L, directory.smallestObservedRejected()),
                    () -> assertEquals(
                            2048L, directory.smallestObservedOomeOrTimeout()),
                    () -> assertFalse(directory.frontierNotReached()),
                    () -> assertTrue(summary.contains(
                            "EXPLORATORY — NON-NORMATIVE — NOT A SAFETY CEILING")),
                    () -> assertTrue(summary.contains(
                            P4E0ResearchMatrixPlan.DISCLAIMER)),
                    () -> assertTrue(summary.contains("largest_observed_completed")),
                    () -> assertTrue(summary.contains("smallest_observed_failed")),
                    () -> assertTrue(summary.contains("frontier_not_reached")),
                    () -> assertTrue(summary.contains(
                            "PER_RECORD_TEARDOWN_NO_EXPLICIT_GC")),
                    () -> assertTrue(summary.contains(
                            "aggregate_per_record_teardown_no_explicit_gc")),
                    () -> assertFalse(summary.contains("recommended" + "_max")),
                    () -> assertFalse(summary.contains("safe" + "_max")),
                    () -> assertFalse(summary.contains("production" + "_limit")),
                    () -> assertFalse(summary.contains("authority" + "_value")),
                    () -> assertTrue(Files.isRegularFile(root.resolve(
                            P4E0ResearchReportAggregator.FIXTURE_MANIFEST_JSON))),
                    () -> assertThrows(
                            IOException.class,
                            () -> P4E0ResearchReportAggregator.writeStudy(
                                    root, plan, manifest, records)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void stopClassificationCanBeRetainedPartiallyButCannotPublishACompleteStudy()
            throws Exception {
        var spec = runSpec(0, 64, "fixture-0");
        var plan = new P4E0ResearchMatrixPlan(List.of(spec));
        var manifest = fixtureManifest(plan);
        var stopped = record(
                plan,
                spec,
                P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                new P4E0ResearchRunRecord.ProcessResult(
                        1, false, false, false, "java.lang.IllegalStateException"));
        var root = projectRoot().resolve(
                "build/reports/p4-e0-research/report-stop-unit");
        deleteTree(root);
        try {
            Files.createDirectories(root);
            P4E0ResearchReportAggregator.writePartialJsonl(
                    root.resolve(P4E0ResearchReportAggregator.PARTIAL_RUNS_JSONL),
                    List.of(stopped));
            assertAll(
                    () -> assertEquals(
                            P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                            P4E0ResearchReportAggregator.readJsonl(
                                            root.resolve(P4E0ResearchReportAggregator
                                                    .PARTIAL_RUNS_JSONL),
                                            1)
                                    .getFirst().classification()),
                    () -> assertThrows(
                            IOException.class,
                            () -> P4E0ResearchReportAggregator.writeStudy(
                                    root, plan, manifest, List.of(stopped))),
                    () -> assertFalse(Files.exists(
                            root.resolve(P4E0ResearchReportAggregator.RUNS_JSONL))));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void completedSemanticNegativeNormalizesExactAndOverLimitRootShapes()
            throws Exception {
        var exact = rootSpec(0, "EXACT_ALL_DISTINCT", 65_536L, "root-exact");
        var over = rootSpec(1, "OVER_LIMIT_ALL_DISTINCT", 65_537L, "root-over");
        var plan = new P4E0ResearchMatrixPlan(List.of(exact, over));
        var records = List.of(
                record(
                        plan,
                        exact,
                        P4E0ResearchResult.Classification.COMPLETED,
                        new P4E0ResearchRunRecord.ProcessResult(
                                0, false, false, true, ""),
                        Map.of("workload_admitted", 1L)),
                record(
                        plan,
                        over,
                        P4E0ResearchResult.Classification.COMPLETED,
                        new P4E0ResearchRunRecord.ProcessResult(
                                0, false, false, true, ""),
                        Map.of("workload_admitted", 0L)));
        var frontiers = P4E0ResearchReportAggregator.frontiers(records);

        assertAll(
                () -> assertEquals(1, frontiers.size()),
                () -> assertEquals("ALL_DISTINCT",
                        frontiers.keySet().iterator().next().shape()),
                () -> assertEquals(65_536L,
                        frontiers.values().iterator().next().largestObservedCompleted()),
                () -> assertEquals(65_537L,
                        frontiers.values().iterator().next().smallestObservedFailed()),
                () -> assertEquals(65_537L,
                        frontiers.values().iterator().next().smallestObservedRejected()),
                () -> assertFalse(
                        frontiers.values().iterator().next().frontierNotReached()));
    }

    @Test
    void unexecutedConditionalDirectoryPointDoesNotBecomeAnObservedFrontier() {
        var observed = runSpec(0, 16_384L, "directory-observed");
        var skipped = runSpec(1, 32_768L, "directory-conditional-skipped");
        var plan = new P4E0ResearchMatrixPlan(List.of(observed, skipped));
        var records = List.of(
                record(
                        plan,
                        observed,
                        P4E0ResearchResult.Classification.COMPLETED,
                        new P4E0ResearchRunRecord.ProcessResult(
                                0, false, false, true, "")),
                record(
                        plan,
                        skipped,
                        P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD,
                        new P4E0ResearchRunRecord.ProcessResult(
                                1, false, false, true,
                                "com.yo1no.gramarye.ResearchGuardException"),
                        Map.of(
                                "conditional_extension", 1L,
                                "conditional_eligible", 1L,
                                "conditional_executed", 0L)));

        var frontier = P4E0ResearchReportAggregator.frontiers(records)
                .values().iterator().next();
        assertAll(
                () -> assertEquals(16_384L, frontier.largestObservedCompleted()),
                () -> assertEquals(null, frontier.smallestObservedFailed()),
                () -> assertEquals(null, frontier.smallestObservedRejected()),
                () -> assertEquals(null, frontier.smallestObservedOomeOrTimeout()),
                () -> assertTrue(frontier.frontierNotReached()));
    }

    private static P4E0ResearchMatrixPlan fixturePlan() {
        return new P4E0ResearchMatrixPlan(List.of(
                runSpec(0, 64, "fixture-0"),
                runSpec(1, 256, "fixture-1"),
                runSpec(2, 1024, "fixture-2"),
                runSpec(3, 2048, "fixture-3"),
                new P4E0ResearchMatrixPlan.RunSpec(
                        4,
                        "run-4",
                        P4E0ResearchMatrixPlan.Mode.DEDICATED,
                        P4E0ResearchMatrixPlan.Matrix.F_COMBINED,
                        P4E0ResearchMatrixPlan.FrontierKind.HEAP_FOR_FIXED_PROFILE,
                        "heap_mib",
                        "COMBINED",
                        "BALANCED",
                        1024,
                        1024,
                        "MIB_HEAP",
                        870,
                        "fixture-4",
                        Map.of("disk_budget_bytes", 34_359_738_368L))));
    }

    private static P4E0ResearchMatrixPlan.RunSpec runSpec(
            int index, long coordinate, String fixtureId) {
        return new P4E0ResearchMatrixPlan.RunSpec(
                index,
                "run-" + index,
                P4E0ResearchMatrixPlan.Mode.PLAIN,
                P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY,
                P4E0ResearchMatrixPlan.FrontierKind.WORKLOAD_AT_FIXED_HEAP,
                "directory_entries",
                "ZERO_ROOT",
                "",
                1024,
                coordinate,
                "RECORDS",
                540,
                fixtureId,
                Map.of("directory_entries", coordinate));
    }

    private static P4E0ResearchMatrixPlan.RunSpec rootSpec(
            int index, String shape, long coordinate, String fixtureId) {
        return new P4E0ResearchMatrixPlan.RunSpec(
                index,
                "root-run-" + index,
                P4E0ResearchMatrixPlan.Mode.PLAIN,
                P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE,
                P4E0ResearchMatrixPlan.FrontierKind.WORKLOAD_AT_FIXED_HEAP,
                "RAW_ROOTS",
                shape,
                "",
                1024,
                coordinate,
                "references",
                600,
                fixtureId,
                Map.of());
    }

    private static List<P4E0ResearchRunRecord> fixtureRecords(
            P4E0ResearchMatrixPlan plan) {
        return List.of(
                record(
                        plan,
                        plan.requireRun(0),
                        P4E0ResearchResult.Classification.COMPLETED,
                        new P4E0ResearchRunRecord.ProcessResult(
                                0, false, false, true, "")),
                record(
                        plan,
                        plan.requireRun(1),
                        P4E0ResearchResult.Classification.COMPLETED,
                        new P4E0ResearchRunRecord.ProcessResult(
                                0, false, false, true, "")),
                record(
                        plan,
                        plan.requireRun(2),
                        P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD,
                        new P4E0ResearchRunRecord.ProcessResult(
                                1, false, false, true,
                                "com.yo1no.gramarye.ResearchGuardException")),
                record(
                        plan,
                        plan.requireRun(3),
                        P4E0ResearchResult.Classification.TIMEOUT,
                        new P4E0ResearchRunRecord.ProcessResult(
                                124, true, false, false,
                                "java.util.concurrent.TimeoutException")),
                record(
                        plan,
                        plan.requireRun(4),
                        P4E0ResearchResult.Classification.OOME_EXIT,
                        new P4E0ResearchRunRecord.ProcessResult(
                                3, false, true, false, "java.lang.OutOfMemoryError")));
    }

    private static P4E0ResearchRunRecord record(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchResult.Classification classification,
            P4E0ResearchRunRecord.ProcessResult processResult) {
        return record(plan, spec, classification, processResult, Map.of());
    }

    private static P4E0ResearchRunRecord record(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchMatrixPlan.RunSpec spec,
            P4E0ResearchResult.Classification classification,
            P4E0ResearchRunRecord.ProcessResult processResult,
            Map<String, Long> additionalMetrics) {
        var metrics = new java.util.TreeMap<String, Long>();
        metrics.put("sampled_peak_used", 1000L + spec.runIndex());
        metrics.put("cumulative_bytes", 2000L + spec.runIndex());
        metrics.put("cumulative_cpu_millis", 3000L + spec.runIndex());
        if (spec.matrix() == P4E0ResearchMatrixPlan.Matrix.F_COMBINED) {
            metrics.put("selected_directory_run_index", 0L);
            metrics.put("selected_playerdata_run_index", 1L);
            metrics.put("selected_physical_bytes", 4_096L);
            metrics.put("selected_decompressed_bytes", 8_192L);
        }
        metrics.putAll(additionalMetrics);
        return new P4E0ResearchRunRecord(
                STUDY_ID,
                plan.planHash(),
                spec,
                classification,
                processResult,
                100L + spec.runIndex(),
                environment(),
                metrics,
                new P4E0ResearchRunRecord.FixtureEvidence(
                        spec.fixtureId(),
                        P4E0ResearchHashing.sha256(spec.fixtureId()),
                        spec.coordinate(),
                        spec.coordinate(),
                        spec.coordinate()));
    }

    private static P4E0ResearchRunRecord.Environment environment() {
        return new P4E0ResearchRunRecord.Environment(
                "21.0.8", "OpenJDK 64-Bit Server VM", "Test OS", "test-arch",
                "test-store", "testfs");
    }

    private static P4E0ResearchFixtureManifest fixtureManifest(
            P4E0ResearchMatrixPlan plan) {
        var entries = new ArrayList<P4E0ResearchFixtureManifest.Entry>();
        for (var spec : plan.runs()) {
            entries.add(new P4E0ResearchFixtureManifest.Entry(
                    spec.fixtureId(),
                    spec.matrix(),
                    spec.axis(),
                    spec.shape(),
                    spec.coordinate(),
                    1,
                    spec.coordinate(),
                    spec.coordinate(),
                    spec.coordinate(),
                    P4E0ResearchHashing.sha256(spec.fixtureId()),
                    "DETERMINISTIC_TEST_FIXTURE"));
        }
        return new P4E0ResearchFixtureManifest(
                GIT_HEAD,
                STUDY_ID,
                plan.planHash(),
                88201651008049L,
                34_359_738_368L,
                "3".repeat(64),
                P4E0ResearchFixtureManifest.BaseFixtureVerification.VERIFIED,
                plan.runCount(),
                entries,
                List.of(new P4E0ResearchFixtureManifest.ConditionalPoint(
                        "conditional-skipped",
                        P4E0ResearchFixtureManifest.ConditionalDecision.SKIPPED,
                        "ELAPSED_THRESHOLD_NOT_MET",
                        List.of("run-0"))));
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("project root not found");
    }
}
