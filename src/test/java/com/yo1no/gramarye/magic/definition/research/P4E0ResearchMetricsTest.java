package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

/** Exact multidimensional counter and bounded-result-schema gate for P4-E0-R1. */
final class P4E0ResearchMetricsTest {
    private static final Path PROJECT_ROOT = projectRoot();
    private static final Path RESEARCH_PACKAGE = PROJECT_ROOT.resolve(
            "src/p4E0Research/java/com/yo1no/gramarye/magic/definition/research");
    private static final Path METRICS =
            RESEARCH_PACKAGE.resolve("P4E0ResearchNbtMetrics.java");
    private static final Path RESULT =
            RESEARCH_PACKAGE.resolve("P4E0ResearchResult.java");

    @Test
    void nbtMetricsExposeEveryRequiredIndependentCoordinate() {
        var metrics = read(METRICS);
        var fullSchema = metrics + '\n' + read(RESULT);
        for (var key : P4E0ResearchPhaseTypes.REQUIRED_METRIC_KEYS) {
            assertTrue(containsSnakeOrCamel(fullSchema, key),
                    () -> "missing independent metric coordinate " + key);
        }
        for (var extraArrayCoordinate : List.of(
                "byteArrayCount", "intArrayCount", "longArrayCount")) {
            assertTrue(metrics.contains(extraArrayCoordinate),
                    () -> "zero-length arrays need tag coordinate " + extraArrayCoordinate);
        }
    }

    @Test
    void countersUseCheckedLongArithmeticAndContainerDepthStartsAtRoot() {
        var metrics = read(METRICS);
        assertAll(
                () -> assertTrue(metrics.contains("Math.addExact")),
                () -> assertTrue(metrics.contains("long ") || metrics.contains("Long")),
                () -> assertTrue(metrics.contains("modifiedUtf8Bytes")),
                () -> assertTrue(metrics.contains("maxContainerDepth")),
                () -> assertTrue(metrics.contains("root")),
                () -> assertFalse(metrics.contains("saturat")),
                () -> assertFalse(metrics.contains("Integer.MAX_VALUE")),
                () -> assertFalse(metrics.contains("NbtAccounter.unlimitedHeap")));
    }

    @Test
    void modifiedUtfLengthMatchesLockedDataOutputFraming() throws Exception {
        for (var value : List.of("", "A", "\0", "\u007f", "\u0080", "\u07ff",
                "\u0800", "\ud83d\ude00", "A\0\u0080\u0800")) {
            assertEquals(
                    dataOutputModifiedUtfPayloadBytes(value),
                    P4E0ResearchNbtMetrics.modifiedUtf8Length(value),
                    () -> "modified UTF length mismatch for "
                            + Integer.toHexString(value.hashCode()));
        }
        assertThrows(ArithmeticException.class,
                () -> P4E0ResearchNbtMetrics.add(Long.MAX_VALUE, 1L));
    }

    @Test
    void logicalWalkerCountsRootDepthContainersArraysAndValuesIndependently() {
        var root = new CompoundTag();
        root.putString("ascii", "A");
        root.putString("nul", "\0");
        var list = new ListTag();
        var nested = new CompoundTag();
        nested.putByteArray("bytes", new byte[0]);
        nested.putIntArray("ints", new int[] {1, 2});
        nested.putLongArray("longs", new long[] {3L});
        nested.putInt("n", 4);
        list.add(nested);
        root.put("nested", list);

        var metrics = P4E0ResearchNbtMetrics.measure(root);
        assertAll(
                () -> assertEquals(3L, metrics.maxContainerDepth()),
                () -> assertEquals(2L, metrics.compoundCount()),
                () -> assertEquals(7L, metrics.compoundEntryCount()),
                () -> assertEquals(1L, metrics.listCount()),
                () -> assertEquals(1L, metrics.listElementCount()),
                () -> assertEquals(3L, metrics.scalarTagCount()),
                () -> assertEquals(1L, metrics.byteArrayCount()),
                () -> assertEquals(0L, metrics.byteArrayElements()),
                () -> assertEquals(1L, metrics.intArrayCount()),
                () -> assertEquals(2L, metrics.intArrayElements()),
                () -> assertEquals(1L, metrics.longArrayCount()),
                () -> assertEquals(1L, metrics.longArrayElements()),
                () -> assertEquals(2L, metrics.stringCount()),
                () -> assertEquals(32L, metrics.modifiedUtf8Bytes()),
                () -> assertEquals(9L, metrics.tagCountTotal()),
                () -> assertEquals(11L, metrics.valueElementsTotal()),
                () -> assertEquals(
                        1L,
                        P4E0ResearchNbtMetrics.measure(new CompoundTag())
                                .maxContainerDepth()));
    }

    @Test
    void resultSchemaContainsOnlyBoundedMetricsHashesAndMachineClassification() {
        var result = read(RESULT);
        for (var key : P4E0ResearchPhaseTypes.RESULT_TOP_LEVEL_KEYS) {
            assertTrue(result.contains('"' + key + '"') || containsSnakeOrCamel(result, key),
                    () -> "missing result schema key " + key);
        }
        for (var classification : P4E0ResearchPhaseTypes.CLASSIFICATION_NAMES) {
            assertTrue(result.contains(classification),
                    () -> "missing bounded classification " + classification);
        }
        for (var forbidden : List.of(
                "rawNbt",
                "raw_nbt",
                "uuidList",
                "uuid_list",
                "fullStoreEntries",
                "full_store_entries",
                "fullJournalEntries",
                "full_journal_entries",
                "rawStore",
                "raw_store",
                "rawJournal",
                "raw_journal",
                "journalEntryList",
                "journal_entry_list",
                "draftPayload",
                "draft_payload",
                "stackTrace",
                "stack_trace",
                "exceptionMessage",
                "exception_message",
                "getMessage()",
                "printStackTrace")) {
            assertFalse(result.contains(forbidden),
                    () -> "unbounded/sensitive result field " + forbidden);
        }
        assertAll(
                () -> assertTrue(result.contains("MAXIMUM_JSON_BYTES = 65_536")),
                () -> assertTrue(result.contains(
                        "text.getBytes(StandardCharsets.UTF_8).length")),
                () -> assertFalse(result.matches(
                        "(?s).*(List|Set|Map)<[^>]*(Journal|Store).*")));
    }

    @Test
    void eachPlayerdataManifestEntryCarriesItsIndependentMetricVectorAndAlias() {
        var nbt = P4E0ResearchNbtMetrics.measure(new CompoundTag());
        var wire = new P4E0ResearchResult.WireMetrics(11, 12, 13, 14, 3, 11, 14);
        var attachment = new P4E0ResearchResult.AttachmentMetrics(15, 1, 2, 3, 4);
        var roots = new P4E0ResearchResult.RootMetrics(
                5, 5, 4, "NOT_APPLICABLE", "NOT_APPLICABLE");
        var manifest = new P4E0ResearchResult.FixtureManifest(
                P4E0ResearchCase.READY_ROOT_MAX,
                "stable-synthetic-alias",
                11,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "READY_ROOT_MAX",
                new P4E0ResearchResult.FixtureMetrics(wire, nbt, attachment, roots));

        var json = manifest.toJson();
        assertAll(
                () -> assertEquals(
                        "stable-synthetic-alias", json.get("artifact_alias").getAsString()),
                () -> assertFalse(json.has("artifact")),
                () -> assertTrue(json.has("metrics")),
                () -> assertTrue(json.getAsJsonObject("metrics").has("wire_metrics")),
                () -> assertTrue(json.getAsJsonObject("metrics").has("nbt_metrics")),
                () -> assertTrue(json.getAsJsonObject("metrics").has("attachment_metrics")),
                () -> assertTrue(json.getAsJsonObject("metrics").has("root_metrics")),
                () -> assertFalse(json.toString().matches(
                        ".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*")));
    }

    @Test
    void supportedMaximumManifestWithWorstScalarMetricsStaysWithinJsonBound() {
        assertAll(
                () -> assertEquals(
                        12,
                        P4E0ResearchFixtureFactory
                                .supportedMaximumSupplementalManifestRecords()),
                () -> assertEquals(
                        513,
                        P4E0ResearchFixtureFactory.supportedMaximumTargetDepth()),
                () -> assertEquals(
                        49,
                        P4E0ResearchFixtureFactory.supportedMaximumManifestCount()));

        var maximum = Long.MAX_VALUE;
        // Worst supported scalar shapes; no raw Attachment or large array is allocated here.
        var fixtureMaximum = 16_777_217L;
        var perFixtureNbt = new P4E0ResearchNbtMetrics(
                513L, 514L, 65_537L, 1L,
                256L, 65_537L, 1L, fixtureMaximum,
                1L, 4_096L, 1L, 4_096L,
                256L, fixtureMaximum, 65_537L, fixtureMaximum);
        var nbt = new P4E0ResearchNbtMetrics(
                maximum, maximum, maximum, maximum,
                maximum, maximum, maximum, maximum,
                maximum, maximum, maximum, maximum,
                maximum, maximum, maximum, maximum);
        var perFixtureWire = new P4E0ResearchResult.WireMetrics(
                67_108_864L,
                67_108_864L,
                67_108_864L,
                100_663_296L,
                3L,
                67_108_864L,
                100_663_296L);
        var wire = new P4E0ResearchResult.WireMetrics(
                maximum, maximum, maximum, maximum, maximum, maximum, maximum);
        var perFixtureAttachment = new P4E0ResearchResult.AttachmentMetrics(
                fixtureMaximum, 1L, 32L, 256L, 64L);
        var attachment = new P4E0ResearchResult.AttachmentMetrics(
                maximum, maximum, maximum, maximum, maximum);
        var perFixtureRoots = new P4E0ResearchResult.RootMetrics(
                320L, fixtureMaximum, 256L, "NOT_APPLICABLE", "NOT_APPLICABLE");
        var roots = new P4E0ResearchResult.RootMetrics(
                maximum, maximum, maximum, "NOT_APPLICABLE", "NOT_APPLICABLE");
        var metrics = new P4E0ResearchResult.FixtureMetrics(
                perFixtureWire, perFixtureNbt, perFixtureAttachment, perFixtureRoots);
        var hash = "f".repeat(64);
        var manifests = new ArrayList<P4E0ResearchResult.FixtureManifest>();
        for (var index = 0;
                index < P4E0ResearchFixtureFactory.supportedMaximumManifestCount();
                index++) {
            manifests.add(new P4E0ResearchResult.FixtureManifest(
                    P4E0ResearchCase.DEPTH_LADDER,
                    "aux-depth-configured-target-" + index,
                    fixtureMaximum,
                    hash,
                    "STRICT_WIRE_DRAIN_PLATFORM_DEPTH_REJECTED",
                    metrics));
        }
        var parameters = new P4E0ResearchParameters(
                P4E0ResearchScenario.CORRECTNESS_SMOKE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                maximum,
                maximum,
                P4E0ResearchFixtureFactory.supportedMaximumTargetDepth(),
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                Integer.MAX_VALUE,
                1.0,
                Integer.MAX_VALUE,
                maximum,
                PROJECT_ROOT.resolve("build/p4-e0-research/json-bound"),
                maximum,
                maximum,
                maximum);
        var result = new P4E0ResearchResult(
                parameters,
                manifests,
                maximum,
                new P4E0ResearchResult.HeapMetrics(
                        maximum, maximum, maximum, maximum, maximum, maximum, maximum),
                new P4E0ResearchResult.DirectoryMetrics(
                        maximum, maximum, maximum, maximum, maximum, maximum, maximum),
                wire,
                nbt,
                attachment,
                roots,
                new P4E0ResearchResult.StoreJournalMetrics(
                        maximum, maximum, maximum, maximum, maximum, maximum,
                        "REDUCED_NON_AUTHORITY_SMOKE"),
                new P4E0ResearchResult.Integrity(
                        hash, maximum, hash, maximum, hash),
                Integer.MAX_VALUE,
                P4E0ResearchResult.Classification.COMPLETED,
                "");

        var bytes = result.toBoundedJson().getBytes(StandardCharsets.UTF_8).length;
        assertTrue(bytes <= P4E0ResearchResult.MAXIMUM_JSON_BYTES);
    }

    @Test
    void heapSamplingIsDiagnosticAndNeverControlsPassOrCatchesOome() throws Exception {
        var source = researchSources();
        assertAll(
                () -> assertTrue(source.contains("ManagementFactory.getMemoryMXBean")),
                () -> assertTrue(source.contains("ManagementFactory.getMemoryPoolMXBeans")),
                () -> assertTrue(source.contains("MemoryPoolMXBean")),
                () -> assertTrue(source.contains("initialHeapUsage.getInit()")),
                () -> assertTrue(source.contains("initialHeapUsage.getCommitted()")),
                () -> assertTrue(source.contains("sampled")),
                () -> assertFalse(source.contains("System.gc(")),
                () -> assertFalse(source.contains("freeMemory(")),
                () -> assertFalse(source.matches(
                        "(?s).*catch\\s*\\([^)]*OutOfMemoryError.*")),
                () -> assertFalse(source.contains("heapPoolPeakSum < xmx")),
                () -> assertFalse(source.contains("heapPoolPeakSum > xmx")));
    }

    @Test
    void classificationTaxonomyAndExpectedFailureMappingAreExact() {
        assertAll(
                () -> assertEquals(
                        List.of(
                                P4E0ResearchResult.Classification.COMPLETED,
                                P4E0ResearchResult.Classification
                                        .REJECTED_BY_RESEARCH_GUARD,
                                P4E0ResearchResult.Classification.FIXTURE_INVALID,
                                P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                                P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                                P4E0ResearchResult.Classification.TIMEOUT,
                                P4E0ResearchResult.Classification.OOME_EXIT),
                        List.of(P4E0ResearchResult.Classification.values())),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.REJECTED_BY_RESEARCH_GUARD,
                        P4E0ResearchMain.classifyExpectedFailure(
                                new IllegalArgumentException())),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.FIXTURE_INVALID,
                        P4E0ResearchMain.classifyExpectedFailure(new IOException())),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.FIXTURE_INVALID,
                        P4E0ResearchMain.classifyExpectedFailure(
                                new IllegalStateException())),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                        P4E0ResearchMain.classifyExpectedFailure(new Exception())));
    }

    @Test
    void parentOwnsFixedHeapChildExitTimeoutAndOomeClassification() {
        assertAll(
                () -> assertEquals(
                        List.of(
                                "-Xms512m",
                                "-Xmx1024m",
                                "-XX:+ExitOnOutOfMemoryError"),
                        P4E0ResearchMain.childJvmArguments(1024)),
                () -> assertEquals(
                        List.of(
                                "-Xms512m",
                                "-Xmx768m",
                                "-XX:+ExitOnOutOfMemoryError"),
                        P4E0ResearchMain.childJvmArguments(768)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.TIMEOUT,
                        P4E0ResearchMain.classifyMissingChildReport(0, true)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.OOME_EXIT,
                        P4E0ResearchMain.classifyMissingChildReport(3, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                        P4E0ResearchMain.classifyMissingChildReport(1, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.TIMEOUT,
                        P4E0ResearchMain.classifyDedicatedMissingExit(true)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                        P4E0ResearchMain.classifyDedicatedMissingExit(false)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchMain.childJvmArguments(0)));

        var main = read(RESEARCH_PACKAGE.resolve("P4E0ResearchMain.java"));
        var result = read(RESULT);
        assertAll(
                () -> assertTrue(main.contains("new ProcessBuilder(")),
                () -> assertTrue(main.contains(".inheritIO()")),
                () -> assertTrue(main.contains(
                        "waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS)")),
                () -> assertTrue(main.contains("destroyForcibly()")),
                () -> assertTrue(main.contains("HOTSPOT_EXIT_ON_OOME_CODE = 3")),
                () -> assertTrue(main.contains("Files.deleteIfExists(report)")),
                () -> assertTrue(main.contains(
                        "var childReport = readReportIfBounded(report);")),
                () -> assertTrue(main.contains("var exitCode = child.exitValue();")),
                () -> assertTrue(main.contains(
                        "var childReport = readReportIfBounded(childReportPath);")),
                () -> assertTrue(main.contains("Files.isRegularFile(exitPath)")),
                () -> assertTrue(result.contains(
                        "process.addProperty(\"exit_code\", processExitCode)")),
                () -> assertFalse(main.contains("child.getInputStream(")),
                () -> assertFalse(main.contains("child.getErrorStream(")),
                () -> assertFalse(main.contains(".redirectOutput(")),
                () -> assertFalse(main.contains(".redirectError(")),
                () -> assertFalse(main.contains(".readLine(")),
                () -> assertFalse(result.contains(
                        "process.addProperty(\"exit_code\", 0)")));
    }

    @Test
    void dedicatedRunningMarkerIsExactBoundedAndCorruptionFailsClosed() throws Exception {
        var reportRoot = PROJECT_ROOT.resolve(
                "build/reports/p4-e0-research/running-marker-unit");
        var marker = reportRoot.resolve(P4E0ResearchMain.DEDICATED_RUNNING_MARKER);
        Files.createDirectories(reportRoot);
        Files.deleteIfExists(marker);
        try {
            assertFalse(P4E0ResearchMain.hasExactDedicatedRunningMarker(reportRoot));
            P4E0ResearchMain.markDedicatedRunning(reportRoot);
            assertAll(
                    () -> assertTrue(
                            P4E0ResearchMain.hasExactDedicatedRunningMarker(reportRoot)),
                    () -> assertEquals(
                            P4E0ResearchMain.DEDICATED_RUNNING_CONTENT,
                            Files.readString(marker)),
                    () -> assertEquals(
                            P4E0ResearchMain.DEDICATED_RUNNING_CONTENT
                                    .getBytes(java.nio.charset.StandardCharsets.US_ASCII).length,
                            Files.size(marker)));
            Files.writeString(marker,
                    P4E0ResearchMain.DEDICATED_RUNNING_CONTENT + "TRAILING");
            assertFalse(P4E0ResearchMain.hasExactDedicatedRunningMarker(reportRoot));
        } finally {
            Files.deleteIfExists(marker);
            Files.deleteIfExists(reportRoot);
        }
    }

    @Test
    void researchNeverDefinesNormativePlayerdataCeilingsOrWorkUnits() throws Exception {
        var source = researchSources();
        assertAll(
                () -> assertFalse(source.contains("MAX_PLAYERDATA_NBT_TREE_NODES")),
                () -> assertFalse(source.contains("MAX_PLAYERDATA_AUDIT_WORK_UNITS")),
                () -> assertFalse(source.contains("MAX_PLAYERDATA_DIRECTORY_ENTRIES")),
                () -> assertFalse(source.contains("MAX_PLAYERDATA_RELEVANT_RECORDS")),
                () -> assertFalse(source.contains("MAX_PLAYERDATA_FILE_COMPRESSED_BYTES")),
                () -> assertFalse(source.contains("MAX_PLAYERDATA_FILE_DECOMPRESSED_BYTES")));
    }

    private static boolean containsSnakeOrCamel(String source, String snake) {
        if (source.contains(snake)) {
            return true;
        }
        var camel = new StringBuilder();
        var upper = false;
        for (var character : snake.toCharArray()) {
            if (character == '_') {
                upper = true;
            } else if (upper) {
                camel.append(Character.toUpperCase(character));
                upper = false;
            } else {
                camel.append(character);
            }
        }
        return source.contains(camel);
    }

    private static long dataOutputModifiedUtfPayloadBytes(String value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeUTF(value);
        }
        return bytes.size() - 2L;
    }

    private static String researchSources() throws IOException {
        try (var paths = Files.walk(PROJECT_ROOT.resolve("src/p4E0Research/java"))) {
            var result = new StringBuilder();
            for (var path : paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .sorted()
                    .toList()) {
                result.append(read(path)).append('\n');
            }
            return result.toString();
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new AssertionError("Unable to inspect " + path, exception);
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
