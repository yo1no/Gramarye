package com.yo1no.gramarye.magic.definition.research;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.store.P4E0ResearchRootWorkloads;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Exact grid and lightweight fixture evidence for the non-authoritative P4-E0-R2 study. */
final class P4E0ResearchR2MatrixTest {
    private static final long MEBIBYTE = 1_048_576L;
    private static final long TEST_DISK_BUDGET = 256L * MEBIBYTE;

    @Test
    void standardPlanOwnsEveryRequiredCoordinateAtEveryHeap() {
        var plan = P4E0ResearchR2PlanFactory.standardPlan();
        var counts = plan.runs().stream().collect(Collectors.groupingBy(
                P4E0ResearchMatrixPlan.RunSpec::matrix, Collectors.counting()));
        var runIds = plan.runs().stream()
                .map(P4E0ResearchMatrixPlan.RunSpec::runId)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertEquals(375, plan.runCount()),
                () -> assertEquals(375, runIds.size()),
                () -> assertEquals(
                        P4E0ResearchPhaseTypes.R2_MATRIX_NAMES,
                        java.util.Arrays.stream(P4E0ResearchMatrixPlan.Matrix.values())
                                .map(Enum::name)
                                .collect(Collectors.toSet())),
                () -> assertEquals(140L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)),
                () -> assertEquals(60L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE)),
                () -> assertEquals(80L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY)),
                () -> assertEquals(40L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.D_AGGREGATE_AUDIT)),
                () -> assertEquals(40L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE)),
                () -> assertEquals(15L, counts.get(
                        P4E0ResearchMatrixPlan.Matrix.F_COMBINED)),
                () -> assertEquals(360L, plan.runs().stream()
                        .filter(run -> run.mode() == P4E0ResearchMatrixPlan.Mode.PLAIN)
                        .count()),
                () -> assertEquals(15L, plan.runs().stream()
                        .filter(run -> run.mode() == P4E0ResearchMatrixPlan.Mode.DEDICATED)
                        .count()),
                () -> assertEquals(
                        List.copyOf(java.util.stream.IntStream.range(360, 375)
                                .boxed().toList()),
                        plan.runs().stream()
                                .filter(run -> run.matrix()
                                        == P4E0ResearchMatrixPlan.Matrix.F_COMBINED)
                                .map(P4E0ResearchMatrixPlan.RunSpec::runIndex)
                                .toList()),
                () -> assertTrue(plan.runs().stream()
                        .filter(run -> run.mode() == P4E0ResearchMatrixPlan.Mode.PLAIN)
                        .allMatch(run -> run.timeoutSeconds()
                                == P4E0ResearchMatrixPlan.PLAIN_TIMEOUT_SECONDS)),
                () -> assertTrue(plan.runs().stream()
                        .filter(run -> run.mode() == P4E0ResearchMatrixPlan.Mode.DEDICATED)
                        .allMatch(run -> run.timeoutSeconds()
                                == P4E0ResearchMatrixPlan.DEDICATED_TIMEOUT_SECONDS)));

        var heapsByCoordinate = new HashMap<String, Set<Integer>>();
        for (var run : plan.runs()) {
            var key = run.matrix() + "|" + run.axis() + "|" + run.shape() + "|"
                    + run.profile() + "|"
                    + (run.frontierKind()
                                    == P4E0ResearchMatrixPlan.FrontierKind
                                            .HEAP_FOR_FIXED_PROFILE
                            ? 0L : run.coordinate());
            heapsByCoordinate.computeIfAbsent(key, ignored -> new java.util.TreeSet<>())
                    .add(run.heapMiB());
        }
        assertTrue(heapsByCoordinate.values().stream().allMatch(
                heaps -> heaps.equals(Set.copyOf(P4E0ResearchMatrixPlan.HEAP_GRID_MIB))));
    }

    @Test
    void standardPlanLocksExactAxesShapesAndAdaptiveDirectoryPoints() {
        var plan = P4E0ResearchR2PlanFactory.standardPlan();
        assertAll(
                () -> assertEquals(
                        Set.of(64L, 256L, 1_024L, 4_096L, 16_384L, 32_768L, 65_536L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY,
                                "DIRECTORY_ENTRIES")),
                () -> assertEquals(
                        Set.of("ALL_IRRELEVANT", "ALL_ZERO_ROOT", "PRIMARY_OLD_PAIRED",
                                "ONE_PERCENT_READY"),
                        shapes(plan, P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)),
                () -> assertEquals(
                        Set.of(1L, 4L, 16L, 32L, 64L, 96L, 128L).stream()
                                .map(value -> value * MEBIBYTE)
                                .collect(Collectors.toSet()),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE,
                                "COMPRESSED_BYTES")),
                () -> assertEquals(
                        Set.of(16L, 32L, 64L, 128L, 256L).stream()
                                .map(value -> value * MEBIBYTE)
                                .collect(Collectors.toSet()),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE,
                                "DECOMPRESSED_BYTES")),
                () -> assertEquals(
                        Set.of(64L, 128L, 256L, 512L, 513L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                                "NBT_DEPTH")),
                () -> assertEquals(
                        Set.of(65_536L, 262_144L, 1_048_576L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                                "COMPOUND_ENTRIES")),
                () -> assertEquals(
                        Set.of(65_536L, 262_144L, 1_048_576L, 4_194_304L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                                "LIST_ELEMENTS")),
                () -> assertEquals(
                        Set.of(65_536L, 262_144L, 1_048_576L, 4_194_304L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                                "PRIMITIVE_ARRAY_ELEMENTS")),
                () -> assertEquals(
                        Set.of(64L, 256L, 512L, 1_024L).stream()
                                .map(value -> value * MEBIBYTE)
                                .collect(Collectors.toSet()),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.D_AGGREGATE_AUDIT,
                                "AGGREGATE_COMPRESSED_BYTES")),
                () -> assertEquals(
                        Set.of(256L, 512L, 1_024L, 2_048L).stream()
                                .map(value -> value * MEBIBYTE)
                                .collect(Collectors.toSet()),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.D_AGGREGATE_AUDIT,
                                "AGGREGATE_DECOMPRESSED_BYTES")),
                () -> assertEquals(
                        Set.of(65_536L, 65_537L),
                        coordinates(plan, P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE,
                                "RAW_ROOTS").stream()
                                .filter(value -> value >= 65_536L)
                                .collect(Collectors.toSet())),
                () -> assertEquals(
                        Set.of(
                                "EXACT_ALL_DISTINCT",
                                "OVER_LIMIT_ALL_DISTINCT",
                                "EXACT_NINETY_PERCENT_DUPLICATES",
                                "OVER_LIMIT_NINETY_PERCENT_DUPLICATES",
                                "PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL",
                                "FIRST_MISSING_BEGINNING",
                                "FIRST_MISSING_MIDDLE",
                                "FIRST_MISSING_END"),
                        shapes(plan, P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE)),
                () -> assertEquals(
                        Set.of("BALANCED", "DIRECTORY_HEAVY", "SINGLE_FILE_HEAVY"),
                        plan.runs().stream()
                                .filter(run -> run.matrix()
                                        == P4E0ResearchMatrixPlan.Matrix.F_COMBINED)
                                .map(P4E0ResearchMatrixPlan.RunSpec::profile)
                                .collect(Collectors.toSet())),
                () -> assertEquals(40L, plan.runs().stream()
                        .filter(run -> run.matrix()
                                == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                        .filter(run -> run.coordinate() > 16_384L)
                        .filter(run -> run.parameters().get("conditional_extension") == 1L)
                        .count()),
                () -> assertEquals(100L, plan.runs().stream()
                        .filter(run -> run.matrix()
                                == P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY)
                        .filter(run -> run.coordinate() <= 16_384L)
                        .filter(run -> run.parameters().get("conditional_extension") == 0L)
                        .count()));

        assertAll(
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2PlanFactory.plainRequest(
                                plan.runs().stream()
                                        .filter(run -> run.matrix()
                                                == P4E0ResearchMatrixPlan.Matrix
                                                        .E_ROOT_CAPTURE)
                                        .findFirst()
                                        .orElseThrow(),
                                projectRoot().resolve(
                                        "build/p4-e0-research/matrix/invalid-e"),
                                TEST_DISK_BUDGET)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2PlanFactory.plainRequest(
                                plan.requireRun(360),
                                projectRoot().resolve(
                                        "build/p4-e0-research/matrix/invalid-f"),
                                TEST_DISK_BUDGET)));
    }

    @Test
    void lightweightDirectoryAndOptionalHeaderFixturesUseActualFilesystemShapes()
            throws Exception {
        var plan = P4E0ResearchR2PlanFactory.standardPlan();
        var root = projectRoot().resolve(
                "build/p4-e0-research/matrix/r2-unit/matrix-fixtures");
        deleteTree(root);
        try {
            var zero = runAt(plan, root.resolve("zero"),
                    P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY,
                    "DIRECTORY_ENTRIES", "ALL_ZERO_ROOT", 64L, 1024);
            var paired = runAt(plan, root.resolve("paired"),
                    P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY,
                    "DIRECTORY_ENTRIES", "PRIMARY_OLD_PAIRED", 64L, 1024);
            var optionalHeader = runAt(plan, root.resolve("optional-header"),
                    P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE,
                    "COMPRESSED_BYTES", "OPTIONAL_HEADER", MEBIBYTE, 1024);
            var depth512 = runAt(plan, root.resolve("depth-512"),
                    P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                    "NBT_DEPTH", "DEPTH", 512L, 1024);
            var depth513 = runAt(plan, root.resolve("depth-513"),
                    P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                    "NBT_DEPTH", "DEPTH", 513L, 1024);

            assertAll(
                    () -> assertEquals(64L, zero.fixture().directoryEntries()),
                    () -> assertEquals(64L, zero.fixture().canonicalPrimaries()),
                    () -> assertEquals(0L, zero.fixture().canonicalOld()),
                    () -> assertEquals(64L, zero.fixture().uniqueRoutes()),
                    () -> assertEquals(64L, zero.directory().decodedRecords()),
                    () -> assertEquals(0L, zero.directory().projectedRoots()),
                    () -> assertEquals(64L, paired.fixture().directoryEntries()),
                    () -> assertEquals(32L, paired.fixture().canonicalPrimaries()),
                    () -> assertEquals(32L, paired.fixture().canonicalOld()),
                    () -> assertEquals(32L, paired.fixture().uniqueRoutes()),
                    () -> assertEquals(32L, paired.directory().decodedRecords()),
                    () -> assertEquals(MEBIBYTE, optionalHeader.observedPhysicalBytes()),
                    () -> assertEquals(
                            P4E0ResearchMatrixRunner.ObservationOutcome.MATERIALIZED,
                            depth512.outcome()),
                    () -> assertEquals(512L, depth512.nbt().maxContainerDepth()),
                    () -> assertEquals(
                            P4E0ResearchMatrixRunner.ObservationOutcome
                                    .PLATFORM_DEPTH_REJECTED,
                            depth513.outcome()),
                    () -> assertEquals(513L, depth513.nbt().maxContainerDepth()),
                    () -> assertTrue(optionalHeader.fixture().payloadFiles().stream()
                            .allMatch(Files::isRegularFile)));
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void strictCombinedProfileHandoffRejectsDuplicateUnknownAndTrailingFields()
            throws Exception {
        var root = projectRoot().resolve("build/p4-e0-research/r2-unit/profile-file");
        deleteTree(root);
        try {
            Files.createDirectories(root);
            var value = new P4E0ResearchCombinedProfileFile.Value(
                    "1".repeat(64),
                    360,
                    new P4E0ResearchCombinedEnvelope.Profile(
                            P4E0ResearchCombinedEnvelope.ProfileKind.BALANCED,
                            1024,
                            root.resolve("directory"),
                            64,
                            "ALL_ZERO_ROOT",
                            root.resolve("selected.dat"),
                            "fixture-0",
                            "OPTIONAL_HEADER",
                            1024,
                            2048,
                            "2".repeat(64),
                            4096,
                            4096,
                            4096),
                    "3".repeat(64));
            var path = root.resolve("profile.json");
            P4E0ResearchCombinedProfileFile.writeNew(path, value);
            assertEquals(value, P4E0ResearchCombinedProfileFile.read(path));

            var canonical = Files.readString(path).trim();
            for (var malformed : List.of(
                    canonical.replaceFirst(
                            "\\{\\\"schema_version\\\":0,",
                            "{\"schema_version\":0,\"schema_version\":0,"),
                    canonical.replaceFirst("\\{", "{\"unexpected\":0,"),
                    canonical + "{}")) {
                var malformedPath = root.resolve(
                        "malformed-" + Integer.toUnsignedString(malformed.hashCode()) + ".json");
                Files.writeString(malformedPath, malformed);
                assertThrows(IOException.class,
                        () -> P4E0ResearchCombinedProfileFile.read(malformedPath));
            }
        } finally {
            deleteTree(root);
        }
    }

    @Test
    void rootHarnessBuildsExactAndCapPlusOneDistinctAndDuplicateVectors() {
        var exact = P4E0ResearchRootWorkloads.exactAllDistinct();
        var over = P4E0ResearchRootWorkloads.overLimitAllDistinct();
        var exactDuplicates = P4E0ResearchRootWorkloads.exactNinetyPercentDuplicates();
        var overDuplicates = P4E0ResearchRootWorkloads.overLimitNinetyPercentDuplicates();

        assertAll(
                () -> assertEquals(65_536, exact.metrics().rawRootCount()),
                () -> assertEquals(P4E0ResearchRootWorkloads.Admission.COMPLETE,
                        exact.metrics().admission()),
                () -> assertEquals(65_537, over.metrics().rawRootCount()),
                () -> assertEquals(P4E0ResearchRootWorkloads.Admission.OVER_LIMIT,
                        over.metrics().admission()),
                () -> assertEquals(6_554, exactDuplicates.metrics().distinctRootCount()),
                () -> assertEquals(58_982, exactDuplicates.metrics().duplicateRootCount()),
                () -> assertEquals(6_554, overDuplicates.metrics().distinctRootCount()),
                () -> assertEquals(58_983, overDuplicates.metrics().duplicateRootCount()),
                () -> assertEquals(P4E0ResearchRootWorkloads.Admission.OVER_LIMIT,
                        overDuplicates.metrics().admission()),
                () -> assertFalse(exact.metrics().variant()
                        == over.metrics().variant()));
        exact.retainAtPeak();
        over.retainAtPeak();
        exactDuplicates.retainAtPeak();
        overDuplicates.retainAtPeak();
    }

    @Test
    void diskGuardRejectsReservationBeforeCreatingAnOverBudgetFixture()
            throws Exception {
        var guard = new P4E0ResearchMatrixFixtures.DiskGuard(64L);
        assertThrows(
                P4E0ResearchMatrixFixtures.ResearchGuardException.class,
                () -> guard.reserve(65L));
        assertEquals(0L, guard.committedBytes());
        try (var reservation = guard.reserve(64L)) {
            reservation.commit(63L);
        }
        assertAll(
                () -> assertEquals(63L, guard.committedBytes()),
                () -> assertThrows(
                        P4E0ResearchMatrixFixtures.ResearchGuardException.class,
                        () -> guard.reserve(2L)));
    }

    @Test
    void parentClassificationSeparatesOomeTimeoutMissingReportAndOrdinaryExit() {
        assertAll(
                () -> assertEquals(
                        P4E0ResearchResult.Classification.INSTRUMENTATION_FAILURE,
                        P4E0ResearchR2Main.classifyMissingChildResult(0, false, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                        P4E0ResearchR2Main.classifyMissingChildResult(1, false, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.OOME_EXIT,
                        P4E0ResearchR2Main.classifyMissingChildResult(3, false, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.TIMEOUT,
                        P4E0ResearchR2Main.classifyMissingChildResult(124, false, true)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.CHILD_EXIT_FAILURE,
                        P4E0ResearchR2Main.classifyMissingChildResult(124, false, false)),
                () -> assertEquals(
                        P4E0ResearchResult.Classification.TIMEOUT,
                        P4E0ResearchR2Main.classifyMissingChildResult(1, true, false)),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2Main.classifyMissingChildResult(
                                -1, false, false)),
                () -> assertEquals(
                        12_884_901_888L,
                        P4E0ResearchR2Main.exactPositiveLong(
                                "12884901888", "disk budget")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2Main.exactPositiveLong(
                                "0", "disk budget")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2Main.exactPositiveLong(
                                "01", "disk budget")),
                () -> assertThrows(
                        IllegalArgumentException.class,
                        () -> P4E0ResearchR2Main.exactPositiveLong(
                                "9223372036854775808", "disk budget")));

        var plan = P4E0ResearchR2PlanFactory.standardPlan();
        var combined = plan.requireRun(360);
        var marker = P4E0ResearchR2Main.combinedRunningMarkerContent(combined, plan);
        assertAll(
                () -> assertTrue(marker.startsWith(
                        "P4_E0_R2_COMBINED_RUNNING_V0|360|")),
                () -> assertTrue(marker.contains(combined.runId())),
                () -> assertTrue(marker.contains(plan.planHash())),
                () -> assertTrue(marker.endsWith("\n")),
                () -> assertFalse(marker.contains("\r")));
    }

    private static P4E0ResearchMatrixRunner.RunObservation runAt(
            P4E0ResearchMatrixPlan plan,
            Path fixtureRoot,
            P4E0ResearchMatrixPlan.Matrix matrix,
            String axis,
            String shape,
            long coordinate,
            int heapMiB) throws IOException {
        var spec = plan.runs().stream()
                .filter(run -> run.matrix() == matrix)
                .filter(run -> run.axis().equals(axis))
                .filter(run -> run.shape().equals(shape))
                .filter(run -> run.coordinate() == coordinate)
                .filter(run -> run.heapMiB() == heapMiB)
                .findFirst()
                .orElseThrow();
        var request = P4E0ResearchR2PlanFactory.plainRequest(
                spec, fixtureRoot, TEST_DISK_BUDGET);
        return P4E0ResearchMatrixRunner.prepareAndRun(request);
    }

    private static Set<Long> coordinates(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchMatrixPlan.Matrix matrix,
            String axis) {
        return plan.runs().stream()
                .filter(run -> run.matrix() == matrix && run.axis().equals(axis))
                .map(P4E0ResearchMatrixPlan.RunSpec::coordinate)
                .collect(Collectors.toSet());
    }

    private static Set<String> shapes(
            P4E0ResearchMatrixPlan plan,
            P4E0ResearchMatrixPlan.Matrix matrix) {
        return plan.runs().stream()
                .filter(run -> run.matrix() == matrix)
                .map(P4E0ResearchMatrixPlan.RunSpec::shape)
                .collect(Collectors.toSet());
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
