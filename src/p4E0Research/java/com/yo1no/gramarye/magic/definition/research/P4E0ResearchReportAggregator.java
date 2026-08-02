package com.yo1no.gramarye.magic.definition.research;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic publisher for the four bounded, non-authoritative P4-E0-R2 artifacts. */
final class P4E0ResearchReportAggregator {
    static final String RUNS_JSONL = "runs.jsonl";
    static final String FRONTIERS_CSV = "candidate-frontiers.csv";
    static final String SUMMARY_MARKDOWN = "summary.md";
    static final String FIXTURE_MANIFEST_JSON = "fixture-manifest.json";
    static final String PARTIAL_RUNS_JSONL = "runs.partial.jsonl";
    static final int MAXIMUM_SUMMARY_BYTES = 4 * 1_048_576;
    static final String CSV_HEADER = String.join(",",
            "schema_version", "authority", "run_index", "run_id", "matrix",
            "axis", "shape", "profile", "frontier_kind", "heap_mib",
            "coordinate", "coordinate_unit", "classification",
            "largest_observed_completed", "smallest_observed_failed",
            "smallest_observed_rejected", "smallest_observed_oome_or_timeout",
            "frontier_not_reached", "fixture_hash");
    private static final String NEWLINE = "\n";

    private P4E0ResearchReportAggregator() {
    }

    record FrontierKey(
            P4E0ResearchMatrixPlan.Matrix matrix,
            String axis,
            String shape,
            String profile,
            P4E0ResearchMatrixPlan.FrontierKind kind,
            int fixedHeapMiB) {
        FrontierKey {
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(kind, "kind");
            P4E0ResearchRunRecord.token(axis, 80, false, "axis");
            P4E0ResearchRunRecord.token(shape, 80, false, "shape");
            P4E0ResearchRunRecord.token(profile, 80, true, "profile");
            if (kind == P4E0ResearchMatrixPlan.FrontierKind.WORKLOAD_AT_FIXED_HEAP
                    && !P4E0ResearchMatrixPlan.HEAP_GRID_MIB.contains(fixedHeapMiB)) {
                throw new IllegalArgumentException("frontier heap is outside the grid");
            }
            if (kind == P4E0ResearchMatrixPlan.FrontierKind.HEAP_FOR_FIXED_PROFILE
                    && fixedHeapMiB != 0) {
                throw new IllegalArgumentException("heap frontier cannot pin a heap");
            }
        }

        static FrontierKey of(P4E0ResearchMatrixPlan.RunSpec spec) {
            return new FrontierKey(
                    spec.matrix(), spec.axis(), frontierShape(spec), spec.profile(),
                    spec.frontierKind(),
                    spec.frontierKind()
                                    == P4E0ResearchMatrixPlan.FrontierKind
                                            .WORKLOAD_AT_FIXED_HEAP
                            ? spec.heapMiB() : 0);
        }

        private static String frontierShape(P4E0ResearchMatrixPlan.RunSpec spec) {
            if (spec.matrix() != P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE) {
                return spec.shape();
            }
            return switch (spec.shape()) {
                case "EXACT_ALL_DISTINCT", "OVER_LIMIT_ALL_DISTINCT" ->
                        "ALL_DISTINCT";
                case "EXACT_NINETY_PERCENT_DUPLICATES",
                        "OVER_LIMIT_NINETY_PERCENT_DUPLICATES" ->
                        "NINETY_PERCENT_DUPLICATES";
                default -> spec.shape();
            };
        }
    }

    record Frontier(
            Long largestObservedCompleted,
            Long smallestObservedFailed,
            Long smallestObservedRejected,
            Long smallestObservedOomeOrTimeout,
            boolean frontierNotReached) {
    }

    static void writeStudy(
            Path reportDirectory,
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchFixtureManifest manifest,
            List<P4E0ResearchRunRecord> records) throws IOException {
        Objects.requireNonNull(reportDirectory, "reportDirectory");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(manifest, "manifest");
        var ordered = validateCompleteStudy(plan, manifest, records);
        var frontiers = frontiers(ordered);
        var reportRoot = reportDirectory.toAbsolutePath().normalize();
        var outputs = List.of(
                reportRoot.resolve(RUNS_JSONL),
                reportRoot.resolve(FRONTIERS_CSV),
                reportRoot.resolve(SUMMARY_MARKDOWN),
                reportRoot.resolve(FIXTURE_MANIFEST_JSON));
        for (var output : outputs) {
            if (Files.exists(output)) {
                throw new IOException("completed research output already exists");
            }
        }
        Files.createDirectories(reportRoot);

        var runs = buildJsonl(ordered);
        var csv = buildCsv(ordered, frontiers);
        var summary = buildSummary(manifest, ordered, frontiers);
        var manifestJson = manifest.toBoundedJson() + NEWLINE;
        P4E0ResearchRunRecord.atomicCreate(outputs.get(0), runs);
        P4E0ResearchRunRecord.atomicCreate(outputs.get(1), csv);
        P4E0ResearchRunRecord.atomicCreate(outputs.get(2), summary);
        P4E0ResearchRunRecord.atomicCreate(outputs.get(3), manifestJson);
        verifyPublished(reportRoot, plan);
    }

    static void writePartialJsonl(Path path, List<P4E0ResearchRunRecord> records)
            throws IOException {
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("partial run evidence is empty");
        }
        var ordered = new ArrayList<P4E0ResearchRunRecord>(records);
        ordered.sort(Comparator.comparingInt(record -> record.spec().runIndex()));
        var indices = new HashSet<Integer>();
        for (var record : ordered) {
            if (!indices.add(record.spec().runIndex())) {
                throw new IllegalArgumentException("duplicate partial run index");
            }
        }
        P4E0ResearchRunRecord.atomicCreate(path, buildJsonl(ordered));
    }

    static List<P4E0ResearchRunRecord> readJsonl(Path path, int maximumRecords)
            throws IOException {
        if (maximumRecords <= 0 || !Files.isRegularFile(path)
                || Files.isSymbolicLink(path)) {
            throw new IOException("research JSONL is unavailable");
        }
        var maximumBytes = Math.multiplyExact(
                (long) maximumRecords,
                (long) P4E0ResearchRunRecord.MAXIMUM_JSON_LINE_BYTES + 1L);
        if (Files.size(path) > maximumBytes) {
            throw new IOException("research JSONL exceeds its plan-derived bound");
        }
        var records = new ArrayList<P4E0ResearchRunRecord>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            for (var line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isEmpty() || line.charAt(0) == '\ufeff'
                        || records.size() >= maximumRecords) {
                    throw P4E0ResearchRunRecord.malformed();
                }
                records.add(P4E0ResearchRunRecord.parseLine(line));
            }
        }
        if (records.isEmpty()) {
            throw P4E0ResearchRunRecord.malformed();
        }
        return List.copyOf(records);
    }

    static void verifyPublished(Path reportDirectory, P4E0ResearchMatrixPlan plan)
            throws IOException {
        var reportRoot = reportDirectory.toAbsolutePath().normalize();
        var manifest = P4E0ResearchFixtureManifest.read(
                reportRoot.resolve(FIXTURE_MANIFEST_JSON));
        var records = readJsonl(reportRoot.resolve(RUNS_JSONL), plan.runCount());
        var ordered = validateCompleteStudy(plan, manifest, records);
        var frontiers = frontiers(ordered);
        verifyExactText(reportRoot.resolve(FRONTIERS_CSV), buildCsv(ordered, frontiers));
        verifyExactText(
                reportRoot.resolve(SUMMARY_MARKDOWN),
                buildSummary(manifest, ordered, frontiers));
    }

    static Map<FrontierKey, Frontier> frontiers(List<P4E0ResearchRunRecord> records) {
        var accumulators = new HashMap<FrontierKey, FrontierAccumulator>();
        for (var record : records) {
            var key = FrontierKey.of(record.spec());
            accumulators.computeIfAbsent(key, ignored -> new FrontierAccumulator())
                    .observe(record);
        }
        var keys = new ArrayList<>(accumulators.keySet());
        keys.sort(frontierKeyComparator());
        var result = new LinkedHashMap<FrontierKey, Frontier>();
        keys.forEach(key -> result.put(key, accumulators.get(key).finish()));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static List<P4E0ResearchRunRecord> validateCompleteStudy(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchFixtureManifest manifest,
            List<P4E0ResearchRunRecord> records) throws IOException {
        if (!manifest.planHash().equals(plan.planHash())
                || manifest.plannedRuns() != plan.runCount()
                || records == null || records.size() != plan.runCount()) {
            throw new IOException("research plan, manifest, and run count differ");
        }
        var fixtureEntries = manifest.materializedFixtures().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        P4E0ResearchFixtureManifest.Entry::fixtureId,
                        java.util.function.Function.identity()));
        var ordered = new ArrayList<P4E0ResearchRunRecord>(records);
        ordered.sort(Comparator.comparingInt(record -> record.spec().runIndex()));
        P4E0ResearchRunRecord.Environment environment = null;
        for (var index = 0; index < ordered.size(); index++) {
            var record = ordered.get(index);
            if (!record.spec().equals(plan.requireRun(index))
                    || !record.planHash().equals(plan.planHash())
                    || !record.studyId().equals(manifest.studyId())
                    || !fixtureEntries.containsKey(record.fixture().fixtureId())) {
                throw new IOException("run record does not match its study plan");
            }
            var entry = fixtureEntries.get(record.fixture().fixtureId());
            if (record.classification() == P4E0ResearchResult.Classification.COMPLETED
                    && (!record.fixture().fixtureHash().equals(entry.hash())
                            || record.fixture().physicalBytes() != entry.physicalBytes()
                            || record.fixture().actualCompressedBytes()
                                    != entry.actualCompressedBytes()
                            || record.fixture().actualDecompressedBytes()
                                    != entry.actualDecompressedBytes())) {
                throw new IOException(
                        "completed run evidence differs from its fixture manifest");
            }
            if (!isObservedFrontierClassification(record.classification())) {
                throw new IOException(
                        "study stopped before complete frontier aggregation: "
                                + record.classification().name());
            }
            if (environment == null) {
                environment = record.environment();
            } else if (!environment.equals(record.environment())) {
                throw new IOException("matrix children reported different environments");
            }
        }
        return List.copyOf(ordered);
    }

    private static boolean isObservedFrontierClassification(
            P4E0ResearchResult.Classification classification) {
        return classification == P4E0ResearchResult.Classification.COMPLETED
                || classification
                        == P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD
                || classification == P4E0ResearchResult.Classification.TIMEOUT
                || classification == P4E0ResearchResult.Classification.OOME_EXIT;
    }

    private static String buildJsonl(List<P4E0ResearchRunRecord> records) {
        var expectedBytes = Math.multiplyExact(
                (long) records.size(),
                (long) P4E0ResearchRunRecord.MAXIMUM_JSON_LINE_BYTES + 1L);
        if (expectedBytes > Integer.MAX_VALUE) {
            throw new IllegalStateException("research JSONL cannot be materialized safely");
        }
        var text = new StringBuilder();
        records.forEach(record -> text.append(record.toJsonLine()).append(NEWLINE));
        if (text.toString().getBytes(StandardCharsets.UTF_8).length > expectedBytes) {
            throw new IllegalStateException("research JSONL exceeded its bound");
        }
        return text.toString();
    }

    private static String buildCsv(
            List<P4E0ResearchRunRecord> records,
            Map<FrontierKey, Frontier> frontiers) {
        var csv = new StringBuilder(CSV_HEADER).append(NEWLINE);
        for (var record : records) {
            var spec = record.spec();
            var frontier = frontiers.get(FrontierKey.of(spec));
            csv.append(SCHEMA_VERSION_TEXT).append(',')
                    .append(P4E0ResearchMatrixPlan.AUTHORITY).append(',')
                    .append(spec.runIndex()).append(',')
                    .append(spec.runId()).append(',')
                    .append(spec.matrix().name()).append(',')
                    .append(spec.axis()).append(',')
                    .append(spec.shape()).append(',')
                    .append(spec.profile()).append(',')
                    .append(spec.frontierKind().name()).append(',')
                    .append(spec.heapMiB()).append(',')
                    .append(spec.coordinate()).append(',')
                    .append(spec.coordinateUnit()).append(',')
                    .append(record.classification().name()).append(',')
                    .append(nullable(frontier.largestObservedCompleted())).append(',')
                    .append(nullable(frontier.smallestObservedFailed())).append(',')
                    .append(nullable(frontier.smallestObservedRejected())).append(',')
                    .append(nullable(frontier.smallestObservedOomeOrTimeout())).append(',')
                    .append(frontier.frontierNotReached()).append(',')
                    .append(record.fixture().fixtureHash()).append(NEWLINE);
        }
        return csv.toString();
    }

    private static final String SCHEMA_VERSION_TEXT =
            Integer.toString(P4E0ResearchRunRecord.SCHEMA_VERSION);

    private static String buildSummary(
            P4E0ResearchFixtureManifest manifest,
            List<P4E0ResearchRunRecord> records,
            Map<FrontierKey, Frontier> frontiers) {
        var environment = records.getFirst().environment();
        var counts = new EnumMap<P4E0ResearchResult.Classification, Integer>(
                P4E0ResearchResult.Classification.class);
        for (var classification : P4E0ResearchResult.Classification.values()) {
            counts.put(classification, 0);
        }
        records.forEach(record -> counts.compute(
                record.classification(), (ignored, count) -> count + 1));

        var summary = new StringBuilder();
        summary.append("# P4-E0-R2 empirical candidate matrix\n\n")
                .append("**EXPLORATORY — NON-NORMATIVE — NOT A SAFETY CEILING**\n\n")
                .append(P4E0ResearchMatrixPlan.DISCLAIMER).append("\n\n")
                .append("## Environment\n\n")
                .append("- Git HEAD: `").append(manifest.gitHead()).append("`\n")
                .append("- Java: ").append(markdown(environment.javaVersion()))
                .append(" (").append(markdown(environment.vmName())).append(")\n")
                .append("- OS: ").append(markdown(environment.osName())).append(" / ")
                .append(markdown(environment.osArch())).append("\n")
                .append("- Filesystem: ").append(markdown(environment.fileStoreName()))
                .append(" / ").append(markdown(environment.fileStoreType())).append("\n")
                .append("- Study ID: `").append(manifest.studyId()).append("`\n")
                .append("- Plan hash: `").append(manifest.planHash()).append("`\n")
                .append("- Fixture-root hash: `").append(manifest.fixtureRootHash())
                .append("`\n")
                .append("- Research disk budget bytes: ")
                .append(manifest.diskBudgetBytes()).append("\n\n")
                .append("## Classification counts\n\n")
                .append("| Classification | Runs |\n|---|---:|\n");
        counts.forEach((classification, count) -> summary.append("| ")
                .append(classification.name()).append(" | ").append(count).append(" |\n"));

        summary.append("\n## Heap matrix results\n\n")
                .append("| Run | Matrix | Axis | Shape/profile | Heap MiB | Coordinate | "
                        + "Classification | Elapsed ms | Sampled peak | Cumulative bytes | "
                        + "Cumulative CPU ms |\n")
                .append("|---|---|---|---|---:|---:|---|---:|---:|---:|---:|\n");
        for (var record : records) {
            var spec = record.spec();
            summary.append("| ").append(spec.runId()).append(" | ")
                    .append(spec.matrix().name()).append(" | ")
                    .append(spec.axis()).append(" | ")
                    .append(spec.profile().isEmpty() ? spec.shape() : spec.profile())
                    .append(" | ").append(spec.heapMiB()).append(" | ")
                    .append(spec.coordinate()).append(" | ")
                    .append(record.classification().name()).append(" | ")
                    .append(record.elapsedMillis()).append(" | ")
                    .append(metric(record, "sampled_peak_used")).append(" | ")
                    .append(metric(record, "cumulative_bytes")).append(" | ")
                    .append(metric(record, "cumulative_cpu_millis")).append(" |\n");
        }

        summary.append("\n## Observed frontiers\n\n")
                .append("| Matrix | Axis | Shape/profile | Heap | "
                        + "largest_observed_completed | smallest_observed_failed | "
                        + "smallest_observed_rejected | "
                        + "smallest_observed_oome_or_timeout | frontier_not_reached |\n")
                .append("|---|---|---|---:|---:|---:|---:|---:|---|\n");
        frontiers.forEach((key, frontier) -> summary.append("| ")
                .append(key.matrix().name()).append(" | ")
                .append(key.axis()).append(" | ")
                .append(key.profile().isEmpty() ? key.shape() : key.profile()).append(" | ")
                .append(key.fixedHeapMiB() == 0 ? "" : key.fixedHeapMiB()).append(" | ")
                .append(nullable(frontier.largestObservedCompleted())).append(" | ")
                .append(nullable(frontier.smallestObservedFailed())).append(" | ")
                .append(nullable(frontier.smallestObservedRejected())).append(" | ")
                .append(nullable(frontier.smallestObservedOomeOrTimeout())).append(" | ")
                .append(frontier.frontierNotReached()).append(" |\n"));

        summary.append("\n## Combined profiles\n\n")
                .append("| Profile | Heap MiB | Classification | Directory source | "
                        + "Playerdata source | Selected physical bytes | "
                        + "Selected decompressed bytes | Fixture hash |\n")
                .append("|---|---:|---|---|---|---:|---:|---|\n");
        records.stream()
                .filter(record -> record.spec().matrix()
                        == P4E0ResearchMatrixPlan.Matrix.F_COMBINED)
                .forEach(record -> {
                    var directory = sourceSpec(
                            records, record, "selected_directory_run_index");
                    var playerdata = sourceSpec(
                            records, record, "selected_playerdata_run_index");
                    summary.append("| ")
                            .append(record.spec().profile()).append(" | ")
                            .append(record.spec().heapMiB()).append(" | ")
                            .append(record.classification().name()).append(" | ")
                            .append(directory.runId()).append(" / ")
                            .append(directory.shape()).append(" / ")
                            .append(directory.coordinate()).append(" | ")
                            .append(playerdata.runId()).append(" / ")
                            .append(playerdata.shape()).append(" / ")
                            .append(playerdata.coordinate()).append(" | ")
                            .append(metric(record, "selected_physical_bytes")).append(" | ")
                            .append(metric(record, "selected_decompressed_bytes"))
                            .append(" | `").append(record.fixture().fixtureHash())
                            .append("` |\n");
                });

        summary.append("\n## Fixture integrity\n\n")
                .append("| Fixture | Matrix | Files | Physical bytes | Hash |\n")
                .append("|---|---|---:|---:|---|\n");
        manifest.materializedFixtures().forEach(entry -> summary.append("| ")
                .append(entry.fixtureId()).append(" | ")
                .append(entry.matrix().name()).append(" | ")
                .append(entry.fileCount()).append(" | ")
                .append(entry.physicalBytes()).append(" | `")
                .append(entry.hash()).append("` |\n"));

        summary.append("\n## Measured metric vectors\n\n")
                .append("| Run | Canonical measured metrics |\n")
                .append("|---|---|\n");
        records.forEach(record -> summary.append("| ")
                .append(record.spec().runId()).append(" | `")
                .append(record.metrics().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + '=' + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(";")))
                .append("` |\n"));

        summary.append("\n## Measurement limitations\n\n")
                .append("- Every value is an observed point; unexecuted values are not extrapolated.\n")
                .append("- Heap samples are diagnostics and are not pass criteria.\n")
                .append("- A streaming low peak does not make cumulative work bounded.\n")
                .append("- Matrix D uses PER_RECORD_TEARDOWN_NO_EXPLICIT_GC; "
                        + "the per-run numeric flag is "
                        + "aggregate_per_record_teardown_no_explicit_gc.\n")
                .append("- Results depend on this machine, filesystem, JVM, fixture shape, "
                        + "and implementation revision.\n")
                .append("- This study neither invokes P4-E reclaim composition nor changes "
                        + "Gramarye authority.\n");
        var text = summary.toString();
        if (text.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_SUMMARY_BYTES) {
            throw new IllegalStateException("research summary exceeded its bound");
        }
        return text;
    }

    private static long metric(P4E0ResearchRunRecord record, String key) {
        return record.metrics().getOrDefault(key, 0L);
    }

    private static P4E0ResearchMatrixPlan.RunSpec sourceSpec(
            List<P4E0ResearchRunRecord> records,
            P4E0ResearchRunRecord combined,
            String metricName) {
        var index = Math.toIntExact(combined.metrics().getOrDefault(metricName, -1L));
        if (index < 0 || index >= records.size()) {
            throw new IllegalStateException("combined source index is outside the study");
        }
        return records.get(index).spec();
    }

    private static String nullable(Long value) {
        return value == null ? "" : Long.toString(value);
    }

    private static String markdown(String value) {
        return value.replace("|", "\\|");
    }

    private static void verifyExactText(Path path, String expected) throws IOException {
        if (!Files.isRegularFile(path) || Files.isSymbolicLink(path)
                || Files.size(path) != expected.getBytes(StandardCharsets.UTF_8).length) {
            throw new IOException("research aggregate artifact has the wrong shape");
        }
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                var expectedReader = new java.io.StringReader(expected)) {
            var left = new char[8_192];
            var right = new char[8_192];
            while (true) {
                var actualRead = reader.read(left);
                var expectedRead = expectedReader.read(right);
                if (actualRead != expectedRead) {
                    throw new IOException("research aggregate artifact differs");
                }
                if (actualRead < 0) {
                    return;
                }
                for (var index = 0; index < actualRead; index++) {
                    if (left[index] != right[index]) {
                        throw new IOException("research aggregate artifact differs");
                    }
                }
            }
        }
    }

    private static Comparator<FrontierKey> frontierKeyComparator() {
        return Comparator.comparing((FrontierKey key) -> key.matrix().ordinal())
                .thenComparing(FrontierKey::axis)
                .thenComparing(FrontierKey::shape)
                .thenComparing(FrontierKey::profile)
                .thenComparing(key -> key.kind().ordinal())
                .thenComparingInt(FrontierKey::fixedHeapMiB);
    }

    private static final class FrontierAccumulator {
        private Long largestCompleted;
        private Long smallestFailed;
        private Long smallestRejected;
        private Long smallestOomeOrTimeout;

        void observe(P4E0ResearchRunRecord record) {
            if (record.metrics().getOrDefault("conditional_extension", 0L) == 1L
                    && record.metrics().getOrDefault("conditional_executed", 1L) == 0L) {
                return;
            }
            var coordinate = record.spec().coordinate();
            var classification = record.classification();
            switch (classification) {
                case COMPLETED -> {
                    if (record.metrics().getOrDefault("workload_admitted", 1L) == 0L) {
                        smallestRejected = minimum(smallestRejected, coordinate);
                        smallestFailed = minimum(smallestFailed, coordinate);
                    } else {
                        largestCompleted = maximum(largestCompleted, coordinate);
                    }
                }
                case REJECTED_BY_RESEARCH_GUARD -> {
                    smallestRejected = minimum(smallestRejected, coordinate);
                    smallestFailed = minimum(smallestFailed, coordinate);
                }
                case TIMEOUT, OOME_EXIT -> {
                    smallestOomeOrTimeout = minimum(
                            smallestOomeOrTimeout, coordinate);
                    smallestFailed = minimum(smallestFailed, coordinate);
                }
                case FIXTURE_INVALID, INSTRUMENTATION_FAILURE, CHILD_EXIT_FAILURE ->
                        throw new IllegalArgumentException(
                                "non-observational failure entered a frontier");
            }
        }

        Frontier finish() {
            return new Frontier(
                    largestCompleted,
                    smallestFailed,
                    smallestRejected,
                    smallestOomeOrTimeout,
                    smallestFailed == null);
        }

        private static Long minimum(Long current, long candidate) {
            return current == null ? candidate : Math.min(current, candidate);
        }

        private static Long maximum(Long current, long candidate) {
            return current == null ? candidate : Math.max(current, candidate);
        }
    }
}
