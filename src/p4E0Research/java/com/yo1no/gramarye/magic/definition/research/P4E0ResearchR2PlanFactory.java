package com.yo1no.gramarye.magic.definition.research;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Closed owner of the exact, exploratory P4-E0-R2 run grid. */
final class P4E0ResearchR2PlanFactory {
    static final long MEBIBYTE = 1_048_576L;
    static final long DEFAULT_SEED = 0x5034_4530_5232_0001L;

    private P4E0ResearchR2PlanFactory() {
    }

    static P4E0ResearchMatrixPlan standardPlan() {
        var builder = new Builder();
        addDirectory(builder);
        addSingleFile(builder);
        addComplexity(builder);
        addAggregate(builder);
        addRoots(builder);
        addCombined(builder);
        return new P4E0ResearchMatrixPlan(builder.runs);
    }

    static P4E0ResearchMatrixRunner.RunRequest plainRequest(
            P4E0ResearchMatrixPlan.RunSpec spec,
            Path fixtureRoot,
            long diskBudgetBytes) {
        var matrix = switch (spec.matrix()) {
            case A_DIRECTORY -> P4E0ResearchMatrixRunner.Matrix.A_DIRECTORY;
            case B_SINGLE_FILE -> P4E0ResearchMatrixRunner.Matrix.B_SINGLE_FILE;
            case C_NBT_COMPLEXITY -> P4E0ResearchMatrixRunner.Matrix.C_NBT_COMPLEXITY;
            case D_AGGREGATE_AUDIT -> P4E0ResearchMatrixRunner.Matrix.D_AGGREGATE;
            case E_ROOT_CAPTURE, F_COMBINED -> throw new IllegalArgumentException(
                    "run does not use the A-D fixture engine");
        };
        var axis = P4E0ResearchMatrixRunner.Axis.valueOf(spec.axis());
        var profile = P4E0ResearchMatrixRunner.Profile.valueOf(spec.shape());
        var maximumCompressed = switch (matrix) {
            case A_DIRECTORY -> 32L * MEBIBYTE;
            case B_SINGLE_FILE -> Math.max(
                    160L * MEBIBYTE, Math.addExact(spec.coordinate(), 2L * MEBIBYTE));
            case C_NBT_COMPLEXITY -> 512L * MEBIBYTE;
            case D_AGGREGATE -> 64L * MEBIBYTE;
        };
        var maximumDecompressed = switch (matrix) {
            case A_DIRECTORY -> 64L * MEBIBYTE;
            case B_SINGLE_FILE -> Math.max(
                    320L * MEBIBYTE, Math.addExact(spec.coordinate(), 2L * MEBIBYTE));
            case C_NBT_COMPLEXITY -> 768L * MEBIBYTE;
            case D_AGGREGATE -> 64L * MEBIBYTE;
        };
        var maximumNodes = Math.max(8_500_000L, Math.addExact(spec.coordinate(), 4_096L));
        var maximumArrayElements = Math.max(
                4_300_000L, Math.addExact(spec.coordinate(), 4_096L));
        var quota = Math.max(maximumDecompressed, 768L * MEBIBYTE);
        return new P4E0ResearchMatrixRunner.RunRequest(
                spec.runId(),
                matrix,
                axis,
                profile,
                spec.coordinate(),
                spec.heapMiB(),
                DEFAULT_SEED,
                fixtureRoot,
                diskBudgetBytes,
                maximumCompressed,
                maximumDecompressed,
                maximumNodes,
                maximumArrayElements,
                quota);
    }

    private static void addDirectory(Builder builder) {
        var values = List.of(64L, 256L, 1_024L, 4_096L, 16_384L, 32_768L, 65_536L);
        for (var shape : List.of(
                "ALL_IRRELEVANT",
                "ALL_ZERO_ROOT",
                "PRIMARY_OLD_PAIRED",
                "ONE_PERCENT_READY")) {
            for (var value : values) {
                for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                    builder.workload(
                            P4E0ResearchMatrixPlan.Matrix.A_DIRECTORY,
                            "DIRECTORY_ENTRIES",
                            shape,
                            "",
                            heap,
                            value,
                            "entries",
                            Map.of(
                                    "conditional_extension", value > 16_384L ? 1L : 0L,
                                    "ready_percent", shape.equals("ONE_PERCENT_READY") ? 1L : 0L));
                }
            }
        }
    }

    private static void addSingleFile(Builder builder) {
        var compressed = List.of(
                1L, 4L, 16L, 32L, 64L, 96L, 128L);
        for (var index = 0; index < compressed.size(); index++) {
            var shape = index < 3 ? "OPTIONAL_HEADER" : "LOW_COMPRESSION_PAYLOAD";
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.workload(
                        P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE,
                        "COMPRESSED_BYTES",
                        shape,
                        "",
                        heap,
                        compressed.get(index) * MEBIBYTE,
                        "bytes",
                        Map.of("target_mib", compressed.get(index)));
            }
        }
        var decompressed = List.of(16L, 32L, 64L, 128L, 256L);
        var shapes = List.of(
                "COMPOUND_BREADTH",
                "LIST_BREADTH",
                "LONG_STRINGS",
                "UNRELATED_ATTACHMENT",
                "HIGHLY_COMPRESSIBLE_ARRAY");
        for (var index = 0; index < decompressed.size(); index++) {
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.workload(
                        P4E0ResearchMatrixPlan.Matrix.B_SINGLE_FILE,
                        "DECOMPRESSED_BYTES",
                        shapes.get(index),
                        "",
                        heap,
                        decompressed.get(index) * MEBIBYTE,
                        "bytes",
                        Map.of("target_mib", decompressed.get(index)));
            }
        }
    }

    private static void addComplexity(Builder builder) {
        complexity(builder, "NBT_DEPTH", "DEPTH", List.of(64L, 128L, 256L, 512L, 513L));
        complexity(builder, "COMPOUND_ENTRIES", "COMPOUND_BREADTH",
                List.of(65_536L, 262_144L, 1_048_576L));
        complexity(builder, "LIST_ELEMENTS", "LIST_BREADTH",
                List.of(65_536L, 262_144L, 1_048_576L, 4_194_304L));
        complexity(builder, "PRIMITIVE_ARRAY_ELEMENTS", "LONG_ARRAY",
                List.of(65_536L, 262_144L, 1_048_576L, 4_194_304L));
    }

    private static void complexity(
            Builder builder, String axis, String shape, List<Long> coordinates) {
        for (var coordinate : coordinates) {
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.workload(
                        P4E0ResearchMatrixPlan.Matrix.C_NBT_COMPLEXITY,
                        axis,
                        shape,
                        "",
                        heap,
                        coordinate,
                        axis.equals("NBT_DEPTH") ? "container_depth" : "elements",
                        Map.of());
            }
        }
    }

    private static void addAggregate(Builder builder) {
        aggregate(builder, "AGGREGATE_COMPRESSED_BYTES", "LOW_COMPRESSION_PAYLOAD",
                List.of(64L, 256L, 512L, 1_024L));
        aggregate(builder, "AGGREGATE_DECOMPRESSED_BYTES", "HIGHLY_COMPRESSIBLE_ARRAY",
                List.of(256L, 512L, 1_024L, 2_048L));
    }

    private static void aggregate(
            Builder builder, String axis, String shape, List<Long> mebibytes) {
        for (var value : mebibytes) {
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.workload(
                        P4E0ResearchMatrixPlan.Matrix.D_AGGREGATE_AUDIT,
                        axis,
                        shape,
                        "",
                        heap,
                        value * MEBIBYTE,
                        "bytes",
                        Map.of("target_mib", value));
            }
        }
    }

    private static void addRoots(Builder builder) {
        var variants = List.of(
                new RootCoordinate("EXACT_ALL_DISTINCT", 65_536L),
                new RootCoordinate("OVER_LIMIT_ALL_DISTINCT", 65_537L),
                new RootCoordinate("EXACT_NINETY_PERCENT_DUPLICATES", 65_536L),
                new RootCoordinate("OVER_LIMIT_NINETY_PERCENT_DUPLICATES", 65_537L),
                new RootCoordinate("PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL", 4_416L),
                new RootCoordinate("FIRST_MISSING_BEGINNING", 65_536L),
                new RootCoordinate("FIRST_MISSING_MIDDLE", 65_536L),
                new RootCoordinate("FIRST_MISSING_END", 65_536L));
        for (var variant : variants) {
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.workload(
                        P4E0ResearchMatrixPlan.Matrix.E_ROOT_CAPTURE,
                        "RAW_ROOTS",
                        variant.name,
                        "",
                        heap,
                        variant.coordinate,
                        "references",
                        Map.of("journal_roots",
                                variant.name.equals("PLAYER_ROOTS_PLUS_MAXIMUM_JOURNAL")
                                        ? 4_096L : 0L));
            }
        }
    }

    private static void addCombined(Builder builder) {
        for (var profile : List.of(
                "BALANCED", "DIRECTORY_HEAVY", "SINGLE_FILE_HEAVY")) {
            for (var heap : P4E0ResearchMatrixPlan.HEAP_GRID_MIB) {
                builder.combined(profile, heap);
            }
        }
    }

    private record RootCoordinate(String name, long coordinate) {
    }

    private static final class Builder {
        private final List<P4E0ResearchMatrixPlan.RunSpec> runs = new ArrayList<>();

        private void workload(
                P4E0ResearchMatrixPlan.Matrix matrix,
                String axis,
                String shape,
                String profile,
                int heapMiB,
                long coordinate,
                String unit,
                Map<String, Long> parameters) {
            var identity = String.format(
                    Locale.ROOT,
                    "%s-%s-%s-%d-%d",
                    matrix.name().toLowerCase(Locale.ROOT),
                    axis.toLowerCase(Locale.ROOT),
                    shape.toLowerCase(Locale.ROOT),
                    coordinate,
                    heapMiB);
            add(new P4E0ResearchMatrixPlan.RunSpec(
                    runs.size(),
                    identity,
                    P4E0ResearchMatrixPlan.Mode.PLAIN,
                    matrix,
                    P4E0ResearchMatrixPlan.FrontierKind.WORKLOAD_AT_FIXED_HEAP,
                    axis,
                    shape,
                    profile,
                    heapMiB,
                    coordinate,
                    unit,
                    P4E0ResearchMatrixPlan.PLAIN_TIMEOUT_SECONDS,
                    identity.substring(0, identity.lastIndexOf('-')),
                    parameters));
        }

        private void combined(String profile, int heapMiB) {
            var identity = String.format(
                    Locale.ROOT, "f-combined-%s-%d",
                    profile.toLowerCase(Locale.ROOT), heapMiB);
            add(new P4E0ResearchMatrixPlan.RunSpec(
                    runs.size(),
                    identity,
                    P4E0ResearchMatrixPlan.Mode.DEDICATED,
                    P4E0ResearchMatrixPlan.Matrix.F_COMBINED,
                    P4E0ResearchMatrixPlan.FrontierKind.HEAP_FOR_FIXED_PROFILE,
                    "COMBINED_HEAP",
                    profile,
                    profile,
                    heapMiB,
                    heapMiB,
                    "heap_mib",
                    P4E0ResearchMatrixPlan.DEDICATED_TIMEOUT_SECONDS,
                    identity,
                    Map.of(
                            "raw_root_attempt", 65_537L,
                            "journal_entries", 4_096L,
                            "store_bytes", 66_060_348L)));
        }

        private void add(P4E0ResearchMatrixPlan.RunSpec run) {
            runs.add(run);
        }
    }
}
