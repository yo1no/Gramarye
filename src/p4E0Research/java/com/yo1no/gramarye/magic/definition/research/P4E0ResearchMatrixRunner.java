package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import com.yo1no.gramarye.magic.definition.store.P4E0ResearchGzipAdapter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtAccounterException;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Executes one bounded, synthetic Matrix A-D observation against an immutable fixture. */
final class P4E0ResearchMatrixRunner {
    private static final String ATTACHMENT_KEY = "gramarye:player_skills";
    private static final Set<Integer> HEAP_GRID = Set.of(1024, 1280, 1536, 1792, 2048);

    private P4E0ResearchMatrixRunner() {
    }

    enum Matrix {
        A_DIRECTORY,
        B_SINGLE_FILE,
        C_NBT_COMPLEXITY,
        D_AGGREGATE
    }

    enum Axis {
        DIRECTORY_ENTRIES,
        COMPRESSED_BYTES,
        DECOMPRESSED_BYTES,
        NBT_DEPTH,
        COMPOUND_ENTRIES,
        LIST_ELEMENTS,
        PRIMITIVE_ARRAY_ELEMENTS,
        AGGREGATE_COMPRESSED_BYTES,
        AGGREGATE_DECOMPRESSED_BYTES
    }

    enum Profile {
        ALL_IRRELEVANT,
        ALL_ZERO_ROOT,
        PRIMARY_OLD_PAIRED,
        ONE_PERCENT_READY,
        OPTIONAL_HEADER,
        LOW_COMPRESSION_PAYLOAD,
        HIGHLY_COMPRESSIBLE_ARRAY,
        COMPOUND_BREADTH,
        LIST_BREADTH,
        LONG_STRINGS,
        UNRELATED_ATTACHMENT,
        DEPTH,
        LONG_ARRAY
    }

    enum ObservationOutcome {
        MATERIALIZED,
        STREAMED_DIRECTORY,
        STREAMED_AGGREGATE_WITH_PER_RECORD_TEARDOWN,
        PLATFORM_DEPTH_REJECTED
    }

    record RunRequest(
            String runId,
            Matrix matrix,
            Axis axis,
            Profile profile,
            long coordinate,
            int heapMiB,
            long seed,
            Path fixtureRoot,
            long diskBudgetBytes,
            long maximumCompressedBytes,
            long maximumDecompressedBytes,
            long maximumNodes,
            long maximumArrayElements,
            long nbtQuotaBytes) {
        RunRequest {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(matrix, "matrix");
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(profile, "profile");
            fixtureRoot = Objects.requireNonNull(fixtureRoot, "fixtureRoot")
                    .toAbsolutePath().normalize();
            if (!runId.matches("[a-z0-9][a-z0-9._-]{0,95}")
                    || coordinate <= 0 || !HEAP_GRID.contains(heapMiB)
                    || diskBudgetBytes <= 0 || maximumCompressedBytes <= 0
                    || maximumDecompressedBytes <= 0 || maximumNodes <= 0
                    || maximumArrayElements <= 0 || nbtQuotaBytes <= 0
                    || diskBudgetBytes == Long.MAX_VALUE
                    || maximumCompressedBytes == Long.MAX_VALUE
                    || maximumDecompressedBytes == Long.MAX_VALUE
                    || maximumNodes == Long.MAX_VALUE
                    || maximumArrayElements == Long.MAX_VALUE
                    || nbtQuotaBytes == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid research matrix request");
            }
            requireCombination(matrix, axis, profile);
        }

        RunRequest withCoordinateAndProfile(long newCoordinate, Profile newProfile, Axis newAxis) {
            return new RunRequest(
                    runId + ".segment",
                    Matrix.B_SINGLE_FILE,
                    newAxis,
                    newProfile,
                    newCoordinate,
                    heapMiB,
                    seed,
                    fixtureRoot,
                    diskBudgetBytes,
                    maximumCompressedBytes,
                    maximumDecompressedBytes,
                    maximumNodes,
                    maximumArrayElements,
                    nbtQuotaBytes);
        }

        RunRequest withFixtureRoot(Path newFixtureRoot, String runIdSuffix) {
            return new RunRequest(
                    runId + Objects.requireNonNull(runIdSuffix, "runIdSuffix"),
                    matrix,
                    axis,
                    profile,
                    coordinate,
                    heapMiB,
                    seed,
                    newFixtureRoot,
                    diskBudgetBytes,
                    maximumCompressedBytes,
                    maximumDecompressedBytes,
                    maximumNodes,
                    maximumArrayElements,
                    nbtQuotaBytes);
        }
    }

    record AggregateMetrics(
            long recordCount,
            long cumulativeCompressedBytes,
            long cumulativeDecompressedBytes,
            long cumulativeCpuNanos,
            String teardownStrategy) {
        AggregateMetrics {
            if (recordCount < 0 || cumulativeCompressedBytes < 0
                    || cumulativeDecompressedBytes < 0 || cumulativeCpuNanos < 0
                    || teardownStrategy == null || teardownStrategy.length() > 80) {
                throw new IllegalArgumentException("invalid aggregate research metrics");
            }
        }

        static AggregateMetrics zero() {
            return new AggregateMetrics(0L, 0L, 0L, 0L, "NOT_APPLICABLE");
        }
    }

    record DirectoryObservation(
            long entries,
            long canonicalPrimaries,
            long canonicalOld,
            long logicalRoutes,
            long irrelevantEntries,
            long decodedRecords,
            long readyRecords,
            long projectedRoots) {
    }

    record RunObservation(
            RunRequest request,
            P4E0ResearchMatrixFixtures.FixtureFacts fixture,
            ObservationOutcome outcome,
            P4E0ResearchNbtMetrics nbt,
            DirectoryObservation directory,
            AggregateMetrics aggregate,
            long observedPhysicalBytes,
            long observedDecompressedBytes,
            long cpuNanos,
            List<Object> retainedAtSamplingPoint) {
        RunObservation {
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(fixture, "fixture");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(nbt, "nbt");
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(aggregate, "aggregate");
            retainedAtSamplingPoint = List.copyOf(retainedAtSamplingPoint);
            if (observedPhysicalBytes < 0 || observedDecompressedBytes < 0
                    || cpuNanos < 0) {
                throw new IllegalArgumentException("negative research observation");
            }
        }

        void retainAtSamplingPoint() {
            retainedAtSamplingPoint.forEach(java.lang.ref.Reference::reachabilityFence);
        }
    }

    static RunObservation prepareAndRun(RunRequest request) throws IOException {
        var guard = new P4E0ResearchMatrixFixtures.DiskGuard(request.diskBudgetBytes());
        var fixture = P4E0ResearchMatrixFixtures.prepare(request, guard);
        return runExisting(request, fixture);
    }

    static RunObservation runExisting(
            RunRequest request,
            P4E0ResearchMatrixFixtures.FixtureFacts fixture) throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(fixture, "fixture");
        var verified = P4E0ResearchMatrixFixtures.verify(request, fixture.root());
        if (!verified.treeHash().equals(fixture.treeHash())
                || !verified.semanticHash().equals(fixture.semanticHash())) {
            throw new IOException("research fixture changed after preparation");
        }
        return switch (request.matrix()) {
            case A_DIRECTORY -> runDirectory(request, verified);
            case B_SINGLE_FILE, C_NBT_COMPLEXITY -> runSingleFile(request, verified);
            case D_AGGREGATE -> runAggregate(request, verified);
        };
    }

    private static RunObservation runDirectory(
            RunRequest request,
            P4E0ResearchMatrixFixtures.FixtureFacts fixture) throws IOException {
        var startedCpu = currentThreadCpu();
        var totalNbt = P4E0ResearchNbtMetrics.zero();
        var decoded = 0L;
        var ready = 0L;
        var roots = 0L;
        var physical = 0L;
        var decompressed = 0L;
        for (var path : fixture.payloadFiles()) {
            var name = path.getFileName().toString();
            if (!name.endsWith(".dat") || name.endsWith(".dat_old")) {
                continue;
            }
            var scan = strictScan(path, request, 512);
            var materialized = P4E0ResearchGzipAdapter.read(
                    path,
                    request.maximumCompressedBytes(),
                    request.maximumDecompressedBytes(),
                    request.nbtQuotaBytes());
            var tag = materialized.decodedRoot();
            requireCurrentDataVersion(tag);
            var materializedMetrics = P4E0ResearchNbtMetrics.measure(tag);
            requireSameLogicalMetrics(scan.nbt(), materializedMetrics);
            totalNbt = totalNbt.plus(materializedMetrics);
            physical = Math.addExact(physical, materialized.physicalFileBytes());
            decompressed = Math.addExact(
                    decompressed, materialized.decompressedRootBytes());
            decoded = Math.addExact(decoded, 1L);
            var admitted = admitAttachment(tag);
            if (admitted.isPresent()) {
                ready = Math.addExact(ready, 1L);
                roots = Math.addExact(
                        roots,
                        admitted.orElseThrow().projectedRoots().orElseThrow().size());
            }
            java.lang.ref.Reference.reachabilityFence(tag);
            java.lang.ref.Reference.reachabilityFence(materialized);
        }
        if (ready != fixture.readyRecords()) {
            throw new IOException("research directory Ready count changed");
        }
        return new RunObservation(
                request,
                fixture,
                ObservationOutcome.STREAMED_DIRECTORY,
                totalNbt,
                new DirectoryObservation(
                        fixture.directoryEntries(),
                        fixture.canonicalPrimaries(),
                        fixture.canonicalOld(),
                        fixture.uniqueRoutes(),
                        fixture.irrelevantEntries(),
                        decoded,
                        ready,
                        roots),
                AggregateMetrics.zero(),
                physical,
                decompressed,
                elapsedCpu(startedCpu),
                List.of());
    }

    private static RunObservation runSingleFile(
            RunRequest request,
            P4E0ResearchMatrixFixtures.FixtureFacts fixture) throws IOException {
        var startedCpu = currentThreadCpu();
        var path = fixture.payloadFiles().getFirst();
        var scanDepth = request.axis() == Axis.NBT_DEPTH
                ? Math.max(513, Math.toIntExact(request.coordinate())) : 512;
        var scan = strictScan(path, request, scanDepth);
        if (request.axis() == Axis.NBT_DEPTH && request.coordinate() == 513L) {
            try {
                NbtIo.readCompressed(path, NbtAccounter.create(request.nbtQuotaBytes()));
                throw new IOException("platform accepted depth-513 research fixture");
            } catch (NbtAccounterException expected) {
                return new RunObservation(
                        request,
                        fixture,
                        ObservationOutcome.PLATFORM_DEPTH_REJECTED,
                        scan.nbt(),
                        emptyDirectory(),
                        AggregateMetrics.zero(),
                        scan.physicalBytes(),
                        scan.decompressedBytes(),
                        elapsedCpu(startedCpu),
                        List.of());
            }
        }

        var materialized = P4E0ResearchGzipAdapter.read(
                path,
                request.maximumCompressedBytes(),
                request.maximumDecompressedBytes(),
                request.nbtQuotaBytes());
        var root = materialized.decodedRoot();
        var metrics = P4E0ResearchNbtMetrics.measure(root);
        requireSameLogicalMetrics(scan.nbt(), metrics);
        return new RunObservation(
                request,
                fixture,
                ObservationOutcome.MATERIALIZED,
                metrics,
                emptyDirectory(),
                AggregateMetrics.zero(),
                materialized.physicalFileBytes(),
                materialized.decompressedRootBytes(),
                elapsedCpu(startedCpu),
                List.of(materialized, root));
    }

    private static RunObservation runAggregate(
            RunRequest request,
            P4E0ResearchMatrixFixtures.FixtureFacts fixture) throws IOException {
        var startedCpu = currentThreadCpu();
        var cumulativeCpu = 0L;
        var physical = 0L;
        var decompressed = 0L;
        var totalNbt = P4E0ResearchNbtMetrics.zero();
        var records = 0L;
        for (var path : fixture.payloadFiles()) {
            var recordCpu = currentThreadCpu();
            var scan = strictScan(path, request, 512);
            var materialized = P4E0ResearchGzipAdapter.read(
                    path,
                    request.maximumCompressedBytes(),
                    request.maximumDecompressedBytes(),
                    request.nbtQuotaBytes());
            var root = materialized.decodedRoot();
            var metrics = P4E0ResearchNbtMetrics.measure(root);
            requireSameLogicalMetrics(scan.nbt(), metrics);
            totalNbt = totalNbt.plus(metrics);
            physical = Math.addExact(physical, materialized.physicalFileBytes());
            decompressed = Math.addExact(
                    decompressed, materialized.decompressedRootBytes());
            records = Math.addExact(records, 1L);
            cumulativeCpu = Math.addExact(cumulativeCpu, elapsedCpu(recordCpu));
            java.lang.ref.Reference.reachabilityFence(root);
            java.lang.ref.Reference.reachabilityFence(materialized);
            // No reference to the decoded tree crosses this iteration; normal GC remains free to
            // reclaim it. The harness never requests or times a collection.
        }
        var observed = request.axis() == Axis.AGGREGATE_COMPRESSED_BYTES
                ? physical : decompressed;
        if (observed < request.coordinate()) {
            throw new IOException("aggregate observation did not reach requested coordinate");
        }
        return new RunObservation(
                request,
                fixture,
                ObservationOutcome.STREAMED_AGGREGATE_WITH_PER_RECORD_TEARDOWN,
                totalNbt,
                emptyDirectory(),
                new AggregateMetrics(
                        records,
                        physical,
                        decompressed,
                        cumulativeCpu,
                        "PER_RECORD_TEARDOWN_NO_EXPLICIT_GC"),
                physical,
                decompressed,
                elapsedCpu(startedCpu),
                List.of());
    }

    private static P4E0ResearchWireNbt.ScanFacts strictScan(
            Path path, RunRequest request, int depth) throws IOException {
        return P4E0ResearchWireNbt.scan(
                path,
                new P4E0ResearchWireNbt.ScanLimits(
                        request.maximumCompressedBytes(),
                        request.maximumDecompressedBytes(),
                        request.maximumNodes(),
                        depth,
                        request.maximumArrayElements()));
    }

    private static Optional<P4E0ResearchAttachmentFixtures.Fixture> admitAttachment(
            CompoundTag playerdata) throws IOException {
        var outer = playerdata.get(AttachmentHolder.ATTACHMENTS_NBT_KEY);
        if (outer == null) {
            return Optional.empty();
        }
        if (!(outer instanceof CompoundTag attachments)) {
            throw new IOException("research Attachment outer tag has wrong type");
        }
        var attachment = attachments.get(ATTACHMENT_KEY);
        if (attachment == null) {
            return Optional.empty();
        }
        var fixture = P4E0ResearchAttachmentFixtures.admit(
                attachment, RegistryAccess.EMPTY);
        if (fixture.variant() != P4E0ResearchAttachmentFixtures.Variant.READY) {
            throw new IOException("research Ready directory admitted quarantine state");
        }
        return Optional.of(fixture);
    }

    private static void requireCurrentDataVersion(CompoundTag root) throws IOException {
        if (!(root.get("DataVersion") instanceof IntTag version)
                || version.getAsInt() != 3_955) {
            throw new IOException("research playerdata DataVersion changed");
        }
    }

    private static void requireSameLogicalMetrics(
            P4E0ResearchNbtMetrics strict, P4E0ResearchNbtMetrics materialized)
            throws IOException {
        if (!strict.equals(materialized)) {
            throw new IOException("strict wire and materialized NBT metrics differ");
        }
    }

    private static DirectoryObservation emptyDirectory() {
        return new DirectoryObservation(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L);
    }

    private static void requireCombination(Matrix matrix, Axis axis, Profile profile) {
        var valid = switch (matrix) {
            case A_DIRECTORY -> axis == Axis.DIRECTORY_ENTRIES
                    && Set.of(
                            Profile.ALL_IRRELEVANT,
                            Profile.ALL_ZERO_ROOT,
                            Profile.PRIMARY_OLD_PAIRED,
                            Profile.ONE_PERCENT_READY).contains(profile);
            case B_SINGLE_FILE -> Set.of(
                            Axis.COMPRESSED_BYTES,
                            Axis.DECOMPRESSED_BYTES).contains(axis)
                    && Set.of(
                            Profile.OPTIONAL_HEADER,
                            Profile.LOW_COMPRESSION_PAYLOAD,
                            Profile.HIGHLY_COMPRESSIBLE_ARRAY,
                            Profile.COMPOUND_BREADTH,
                            Profile.LIST_BREADTH,
                            Profile.LONG_STRINGS,
                            Profile.UNRELATED_ATTACHMENT).contains(profile);
            case C_NBT_COMPLEXITY -> switch (axis) {
                case NBT_DEPTH -> profile == Profile.DEPTH;
                case COMPOUND_ENTRIES -> profile == Profile.COMPOUND_BREADTH;
                case LIST_ELEMENTS -> profile == Profile.LIST_BREADTH;
                case PRIMITIVE_ARRAY_ELEMENTS -> profile == Profile.LONG_ARRAY;
                default -> false;
            };
            case D_AGGREGATE -> Set.of(
                            Axis.AGGREGATE_COMPRESSED_BYTES,
                            Axis.AGGREGATE_DECOMPRESSED_BYTES).contains(axis)
                    && (profile == Profile.LOW_COMPRESSION_PAYLOAD
                            || profile == Profile.HIGHLY_COMPRESSIBLE_ARRAY);
        };
        if (!valid) {
            throw new IllegalArgumentException("invalid matrix axis/profile combination");
        }
    }

    private static long currentThreadCpu() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!bean.isCurrentThreadCpuTimeSupported()) {
            return 0L;
        }
        if (!bean.isThreadCpuTimeEnabled()) {
            bean.setThreadCpuTimeEnabled(true);
        }
        return Math.max(0L, bean.getCurrentThreadCpuTime());
    }

    private static long elapsedCpu(long started) {
        return Math.max(0L, currentThreadCpu() - started);
    }
}
