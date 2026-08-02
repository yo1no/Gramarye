package com.yo1no.gramarye.magic.definition.research;

import com.yo1no.gramarye.magic.definition.player.P4E0ResearchAttachmentFixtures;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.attachment.AttachmentHolder;

/** Deterministic, research-only Matrix A-D fixture preparation and verification. */
final class P4E0ResearchMatrixFixtures {
    private static final long MEBIBYTE = 1_048_576L;
    private static final int CURRENT_DATA_VERSION = 3_955;
    private static final int AGGREGATE_SEGMENT_BYTES = 32 * 1_048_576;
    private static final Pattern PRIMARY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat");
    private static final Pattern OLD = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.dat_old");

    private P4E0ResearchMatrixFixtures() {
    }

    /** Checked research disk reservation; it is not a production disk ceiling. */
    static final class DiskGuard {
        private final long maximumBytes;
        private long committedBytes;
        private long reservedBytes;

        DiskGuard(long maximumBytes) {
            if (maximumBytes <= 0 || maximumBytes == Long.MAX_VALUE) {
                throw new IllegalArgumentException("research disk budget must be finite");
            }
            this.maximumBytes = maximumBytes;
        }

        Reservation reserve(long conservativeBytes) throws ResearchGuardException {
            if (conservativeBytes < 0) {
                throw new IllegalArgumentException("negative research disk reservation");
            }
            var prospective = Math.addExact(
                    committedBytes, Math.addExact(reservedBytes, conservativeBytes));
            if (prospective > maximumBytes) {
                throw new ResearchGuardException("disk_budget");
            }
            reservedBytes = Math.addExact(reservedBytes, conservativeBytes);
            return new Reservation(this, conservativeBytes);
        }

        long maximumBytes() {
            return maximumBytes;
        }

        long committedBytes() {
            return committedBytes;
        }

        private void commit(long reservation, long actual) throws IOException {
            if (actual < 0 || actual > reservation) {
                throw new IOException("research fixture exceeded its disk reservation");
            }
            reservedBytes = Math.subtractExact(reservedBytes, reservation);
            committedBytes = Math.addExact(committedBytes, actual);
        }

        private void cancel(long reservation) {
            reservedBytes = Math.subtractExact(reservedBytes, reservation);
        }
    }

    static final class Reservation implements AutoCloseable {
        private final DiskGuard guard;
        private final long bytes;
        private boolean finished;

        private Reservation(DiskGuard guard, long bytes) {
            this.guard = guard;
            this.bytes = bytes;
        }

        void commit(long actual) throws IOException {
            if (finished) {
                throw new IllegalStateException("research disk reservation already finished");
            }
            guard.commit(bytes, actual);
            finished = true;
        }

        @Override
        public void close() {
            if (!finished) {
                guard.cancel(bytes);
                finished = true;
            }
        }
    }

    static final class ResearchGuardException extends IOException {
        private final String code;

        ResearchGuardException(String code) {
            super("research guard rejected fixture");
            this.code = Objects.requireNonNull(code, "code");
        }

        String code() {
            return code;
        }
    }

    record FixtureFacts(
            Path root,
            List<Path> payloadFiles,
            long fileCount,
            long logicalRecordCount,
            long physicalBytes,
            long decompressedBytes,
            long directoryEntries,
            long canonicalPrimaries,
            long canonicalOld,
            long uniqueRoutes,
            long irrelevantEntries,
            long readyRecords,
            String treeHash,
            String semanticHash) {
        FixtureFacts {
            root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
            payloadFiles = List.copyOf(payloadFiles);
            if (fileCount < 0 || logicalRecordCount < 0 || physicalBytes < 0
                    || decompressedBytes < 0 || directoryEntries < 0
                    || canonicalPrimaries < 0 || canonicalOld < 0
                    || uniqueRoutes < 0 || irrelevantEntries < 0 || readyRecords < 0) {
                throw new IllegalArgumentException("negative research fixture fact");
            }
            requireHash(treeHash);
            requireHash(semanticHash);
        }
    }

    record CombinedInputSpec(
            P4E0ResearchMatrixRunner.RunRequest directory,
            P4E0ResearchMatrixRunner.RunRequest playerdata,
            Path root,
            long diskBudgetBytes) {
        CombinedInputSpec {
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(playerdata, "playerdata");
            root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
            if (directory.matrix() != P4E0ResearchMatrixRunner.Matrix.A_DIRECTORY
                    || playerdata.matrix()
                            != P4E0ResearchMatrixRunner.Matrix.B_SINGLE_FILE
                    || diskBudgetBytes <= 0 || diskBudgetBytes == Long.MAX_VALUE) {
                throw new IllegalArgumentException("invalid combined matrix input spec");
            }
        }
    }

    record CombinedInput(
            Path root,
            FixtureFacts directory,
            FixtureFacts playerdata,
            Path selectedPlayerdata,
            long actualBytes,
            String integrityHash) {
        CombinedInput {
            root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
            Objects.requireNonNull(directory, "directory");
            Objects.requireNonNull(playerdata, "playerdata");
            selectedPlayerdata = Objects.requireNonNull(
                    selectedPlayerdata, "selectedPlayerdata").toAbsolutePath().normalize();
            if (actualBytes < 0) {
                throw new IllegalArgumentException("negative combined input bytes");
            }
            requireHash(integrityHash);
        }
    }

    /**
     * Materializes persistent synthetic A/B inputs selected by the combined-profile orchestrator.
     * The completed directory is never removed or replaced by this method.
     */
    static CombinedInput materializeCombinedInput(CombinedInputSpec spec) throws IOException {
        Objects.requireNonNull(spec, "spec");
        var root = requireCombinedRoot(spec.root());
        if (Files.exists(root)) {
            throw new IOException("combined research input already exists");
        }
        Files.createDirectories(root);
        var guard = new DiskGuard(spec.diskBudgetBytes());
        var directoryRequest = spec.directory().withFixtureRoot(
                root.resolve("directory"), ".combined-directory");
        var playerdataRequest = spec.playerdata().withFixtureRoot(
                root.resolve("selected-playerdata"), ".combined-playerdata");
        var directory = prepare(directoryRequest, guard);
        var playerdata = prepare(playerdataRequest, guard);
        var selected = playerdata.payloadFiles().getFirst();
        var actual = Math.addExact(directory.physicalBytes(), playerdata.physicalBytes());
        if (actual != guard.committedBytes()) {
            throw new IOException("combined input disk accounting changed");
        }
        var hash = P4E0ResearchHashing.sha256(
                directory.treeHash() + '|' + playerdata.treeHash() + '|'
                        + actual + '|' + directoryRequest.profile().name() + '|'
                        + playerdataRequest.profile().name());
        return new CombinedInput(
                root, directory, playerdata, selected, actual, hash);
    }

    static FixtureFacts prepare(
            P4E0ResearchMatrixRunner.RunRequest request, DiskGuard diskGuard)
            throws IOException {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(diskGuard, "diskGuard");
        var root = requireFixtureRoot(request.fixtureRoot());
        if (Files.exists(root)) {
            throw new IOException("research fixture path already exists");
        }
        Files.createDirectories(root);
        try {
            var facts = switch (request.matrix()) {
                case A_DIRECTORY -> prepareDirectory(request, diskGuard, root);
                case B_SINGLE_FILE -> prepareSingleFile(request, diskGuard, root);
                case C_NBT_COMPLEXITY -> prepareComplexity(request, diskGuard, root);
                case D_AGGREGATE -> prepareAggregate(request, diskGuard, root);
            };
            return verify(request, facts.root());
        } catch (IOException | RuntimeException exception) {
            // Partial fixtures are intentionally left for bounded diagnostics; no completed result
            // is removed or rewritten by this helper.
            throw exception;
        }
    }

    static FixtureFacts verify(
            P4E0ResearchMatrixRunner.RunRequest request, Path fixtureRoot)
            throws IOException {
        var root = requireFixtureRoot(fixtureRoot);
        if (!Files.isDirectory(root)) {
            throw new IOException("research fixture directory is missing");
        }
        return switch (request.matrix()) {
            case A_DIRECTORY -> verifyDirectory(request, root);
            case B_SINGLE_FILE, C_NBT_COMPLEXITY -> verifySingleFile(request, root);
            case D_AGGREGATE -> verifyAggregate(request, root);
        };
    }

    private static FixtureFacts prepareDirectory(
            P4E0ResearchMatrixRunner.RunRequest request,
            DiskGuard guard,
            Path root) throws IOException {
        var entries = Math.toIntExact(request.coordinate());
        if (entries <= 0) {
            throw new ResearchGuardException("directory_entries");
        }
        if (request.profile() == P4E0ResearchMatrixRunner.Profile.PRIMARY_OLD_PAIRED
                && (entries & 1) != 0) {
            throw new ResearchGuardException("paired_directory_evenness");
        }
        var conservative = Math.multiplyExact((long) entries, 131_072L);
        try (var reservation = guard.reserve(conservative)) {
            var readyTag = request.profile()
                            == P4E0ResearchMatrixRunner.Profile.ONE_PERCENT_READY
                    ? P4E0ResearchAttachmentFixtures.readyRootMax(false).serializedTag()
                    : null;
            var readyCount = request.profile()
                            == P4E0ResearchMatrixRunner.Profile.ONE_PERCENT_READY
                    ? Math.max(1, Math.toIntExact(Math.floorDiv(entries + 99L, 100L)))
                    : 0;
            var routes = request.profile()
                            == P4E0ResearchMatrixRunner.Profile.PRIMARY_OLD_PAIRED
                    ? entries / 2 : entries;
            for (var index = 0; index < entries; index++) {
                switch (request.profile()) {
                    case ALL_IRRELEVANT -> Files.writeString(
                            root.resolve(String.format(
                                    Locale.ROOT, "irrelevant-%08d.bin", index)),
                            "synthetic-research-only\n",
                            StandardCharsets.US_ASCII,
                            StandardOpenOption.CREATE_NEW);
                    case ALL_ZERO_ROOT -> writePlayerdata(
                            root.resolve(route(request.seed(), index) + ".dat"),
                            playerdata(request.seed(), index, null));
                    case PRIMARY_OLD_PAIRED -> {
                        var routeIndex = index / 2;
                        var route = route(request.seed(), routeIndex);
                        if ((index & 1) == 0) {
                            writePlayerdata(
                                    root.resolve(route + ".dat"),
                                    playerdata(request.seed(), routeIndex, null));
                        } else {
                            Files.writeString(
                                    root.resolve(route + ".dat_old"),
                                    "arbitrary-old-not-selected\n",
                                    StandardCharsets.US_ASCII,
                                    StandardOpenOption.CREATE_NEW);
                        }
                    }
                    case ONE_PERCENT_READY -> writePlayerdata(
                            root.resolve(route(request.seed(), index) + ".dat"),
                            playerdata(
                                    request.seed(), index, index < readyCount ? readyTag : null));
                    default -> throw new ResearchGuardException("directory_profile");
                }
            }
            var actual = treePhysicalBytes(root);
            reservation.commit(actual);
        }
        return verifyDirectory(request, root);
    }

    private static FixtureFacts prepareSingleFile(
            P4E0ResearchMatrixRunner.RunRequest request,
            DiskGuard guard,
            Path root) throws IOException {
        var target = request.coordinate();
        var path = root.resolve("fixture.dat");
        var writer = writerFor(request);
        var header = P4E0ResearchWireNbt.HeaderOptions.canonical();
        var compression = Deflater.DEFAULT_COMPRESSION;
        if (request.profile() == P4E0ResearchMatrixRunner.Profile.OPTIONAL_HEADER) {
            var baseline = P4E0ResearchWireNbt.measure(
                    header,
                    compression,
                    Math.addExact(target, MEBIBYTE),
                    request.maximumDecompressedBytes(),
                    writer);
            var fileNameBytes = Math.toIntExact(
                    Math.subtractExact(target, Math.addExact(baseline.physicalBytes(), 3L)));
            if (fileNameBytes <= 0) {
                throw new ResearchGuardException("optional_header_target");
            }
            // FHCRC adds two bytes and FNAME adds its NUL; target stays exact.
            header = P4E0ResearchWireNbt.HeaderOptions.fileName(fileNameBytes);
        } else if (request.profile()
                == P4E0ResearchMatrixRunner.Profile.LOW_COMPRESSION_PAYLOAD) {
            compression = Deflater.NO_COMPRESSION;
        }
        var reserve = request.axis() == P4E0ResearchMatrixRunner.Axis.COMPRESSED_BYTES
                ? Math.addExact(target, MEBIBYTE)
                : Math.addExact(request.maximumDecompressedBytes(), MEBIBYTE);
        try (var reservation = guard.reserve(reserve)) {
            P4E0ResearchWireNbt.write(
                    path,
                    header,
                    compression,
                    reserve,
                    request.maximumDecompressedBytes(),
                    writer);
            reservation.commit(Files.size(path));
        }
        return verifySingleFile(request, root);
    }

    private static FixtureFacts prepareComplexity(
            P4E0ResearchMatrixRunner.RunRequest request,
            DiskGuard guard,
            Path root) throws IOException {
        var path = root.resolve("fixture.dat");
        var payload = writerFor(request);
        var measured = P4E0ResearchWireNbt.measure(
                P4E0ResearchWireNbt.HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                request.maximumCompressedBytes(),
                request.maximumDecompressedBytes(),
                payload);
        var reservationBytes = Math.addExact(measured.physicalBytes(), 65_536L);
        try (var reservation = guard.reserve(reservationBytes)) {
            P4E0ResearchWireNbt.write(
                    path,
                    P4E0ResearchWireNbt.HeaderOptions.canonical(),
                    Deflater.DEFAULT_COMPRESSION,
                    reservationBytes,
                    request.maximumDecompressedBytes(),
                    payload);
            reservation.commit(Files.size(path));
        }
        return verifySingleFile(request, root);
    }

    private static FixtureFacts prepareAggregate(
            P4E0ResearchMatrixRunner.RunRequest request,
            DiskGuard guard,
            Path root) throws IOException {
        var target = request.coordinate();
        var decompressedAxis = request.axis()
                == P4E0ResearchMatrixRunner.Axis.AGGREGATE_DECOMPRESSED_BYTES;
        var remaining = target;
        var index = 0;
        while (remaining > 0) {
            var segmentTarget = Math.min(remaining, AGGREGATE_SEGMENT_BYTES);
            var segmentRequest = request.withCoordinateAndProfile(
                    segmentTarget,
                    decompressedAxis
                            ? P4E0ResearchMatrixRunner.Profile.HIGHLY_COMPRESSIBLE_ARRAY
                            : P4E0ResearchMatrixRunner.Profile.LOW_COMPRESSION_PAYLOAD,
                    decompressedAxis
                            ? P4E0ResearchMatrixRunner.Axis.DECOMPRESSED_BYTES
                            : P4E0ResearchMatrixRunner.Axis.COMPRESSED_BYTES);
            var payload = writerFor(segmentRequest);
            var compression = decompressedAxis
                    ? Deflater.BEST_COMPRESSION : Deflater.NO_COMPRESSION;
            var maximum = Math.addExact(segmentTarget, 2L * MEBIBYTE);
            try (var reservation = guard.reserve(maximum)) {
                var path = root.resolve(String.format(Locale.ROOT, "record-%05d.dat", index));
                var facts = P4E0ResearchWireNbt.write(
                        path,
                        P4E0ResearchWireNbt.HeaderOptions.canonical(),
                        compression,
                        maximum,
                        Math.addExact(segmentTarget, MEBIBYTE),
                        payload);
                reservation.commit(facts.physicalBytes());
                remaining = Math.subtractExact(
                        remaining,
                        decompressedAxis ? facts.decompressedBytes()
                                : Math.min(remaining, facts.physicalBytes()));
            }
            index++;
            if (index > 131_072) {
                throw new ResearchGuardException("aggregate_record_count");
            }
        }
        return verifyAggregate(request, root);
    }

    private static FixtureFacts verifyDirectory(
            P4E0ResearchMatrixRunner.RunRequest request, Path root) throws IOException {
        var files = regularFiles(root);
        if (files.size() != request.coordinate()) {
            throw new IOException("research directory fixture count differs from coordinate");
        }
        var primary = 0L;
        var old = 0L;
        var irrelevant = 0L;
        var routes = new java.util.HashSet<String>();
        for (var path : files) {
            var name = path.getFileName().toString();
            if (PRIMARY.matcher(name).matches()) {
                primary++;
                routes.add(name.substring(0, 36));
            } else if (OLD.matcher(name).matches()) {
                old++;
                routes.add(name.substring(0, 36));
            } else {
                irrelevant++;
            }
        }
        var expectedReady = request.profile()
                        == P4E0ResearchMatrixRunner.Profile.ONE_PERCENT_READY
                ? Math.max(1L, Math.floorDiv(request.coordinate() + 99L, 100L)) : 0L;
        switch (request.profile()) {
            case ALL_IRRELEVANT -> requireShape(
                    primary == 0 && old == 0 && irrelevant == request.coordinate(),
                    "irrelevant directory");
            case ALL_ZERO_ROOT, ONE_PERCENT_READY -> requireShape(
                    primary == request.coordinate() && old == 0 && irrelevant == 0,
                    "canonical primary directory");
            case PRIMARY_OLD_PAIRED -> requireShape(
                    primary == request.coordinate() / 2
                            && old == request.coordinate() / 2
                            && irrelevant == 0
                            && routes.size() == request.coordinate() / 2,
                    "primary/old paired directory");
            default -> throw new IOException("invalid directory matrix profile");
        }
        return facts(
                root,
                files,
                request.profile() == P4E0ResearchMatrixRunner.Profile.ALL_IRRELEVANT
                        ? 0L
                        : request.profile()
                                        == P4E0ResearchMatrixRunner.Profile.PRIMARY_OLD_PAIRED
                                ? request.coordinate() / 2 : request.coordinate(),
                0L,
                files.size(),
                primary,
                old,
                routes.size(),
                irrelevant,
                expectedReady,
                request);
    }

    private static FixtureFacts verifySingleFile(
            P4E0ResearchMatrixRunner.RunRequest request, Path root) throws IOException {
        var files = regularFiles(root);
        if (files.size() != 1 || !"fixture.dat".equals(
                files.getFirst().getFileName().toString())) {
            throw new IOException("research single-file fixture shape changed");
        }
        var maximumDepth = request.axis() == P4E0ResearchMatrixRunner.Axis.NBT_DEPTH
                ? Math.max(513, Math.toIntExact(request.coordinate())) : 512;
        var scan = P4E0ResearchWireNbt.scan(
                files.getFirst(),
                new P4E0ResearchWireNbt.ScanLimits(
                        request.maximumCompressedBytes(),
                        request.maximumDecompressedBytes(),
                        request.maximumNodes(),
                        maximumDepth,
                        request.maximumArrayElements()));
        if (request.profile() == P4E0ResearchMatrixRunner.Profile.OPTIONAL_HEADER
                && scan.physicalBytes() != request.coordinate()) {
            throw new IOException("optional-header fixture missed exact coordinate");
        }
        return facts(
                root,
                files,
                1L,
                scan.decompressedBytes(),
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                request);
    }

    private static FixtureFacts verifyAggregate(
            P4E0ResearchMatrixRunner.RunRequest request, Path root) throws IOException {
        var files = regularFiles(root);
        if (files.isEmpty()) {
            throw new IOException("research aggregate fixture is empty");
        }
        var compressed = 0L;
        var decompressed = 0L;
        for (var file : files) {
            var scan = P4E0ResearchWireNbt.scan(
                    file,
                    new P4E0ResearchWireNbt.ScanLimits(
                            request.maximumCompressedBytes(),
                            request.maximumDecompressedBytes(),
                            request.maximumNodes(),
                            512,
                            request.maximumArrayElements()));
            compressed = Math.addExact(compressed, scan.physicalBytes());
            decompressed = Math.addExact(decompressed, scan.decompressedBytes());
        }
        var observed = request.axis()
                        == P4E0ResearchMatrixRunner.Axis.AGGREGATE_COMPRESSED_BYTES
                ? compressed : decompressed;
        if (observed < request.coordinate()) {
            throw new IOException("research aggregate fixture did not reach coordinate");
        }
        return facts(
                root,
                files,
                files.size(),
                decompressed,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                request);
    }

    private static FixtureFacts facts(
            Path root,
            List<Path> files,
            long logicalRecords,
            long decompressedBytes,
            long directoryEntries,
            long primary,
            long old,
            long routes,
            long irrelevant,
            long ready,
            P4E0ResearchMatrixRunner.RunRequest request) throws IOException {
        var tree = treeHash(root, files);
        var semantic = P4E0ResearchHashing.sha256(
                request.matrix().name() + '|' + request.axis().name() + '|'
                        + request.profile().name() + '|' + request.coordinate() + '|'
                        + tree.hash() + '|' + files.size());
        return new FixtureFacts(
                root,
                files,
                files.size(),
                logicalRecords,
                tree.physicalBytes(),
                decompressedBytes,
                directoryEntries,
                primary,
                old,
                routes,
                irrelevant,
                ready,
                tree.hash(),
                semantic);
    }

    private static P4E0ResearchWireNbt.PayloadWriter writerFor(
            P4E0ResearchMatrixRunner.RunRequest request) throws IOException {
        return switch (request.profile()) {
            case OPTIONAL_HEADER -> minimalWire(request.seed(), 0);
            case LOW_COMPRESSION_PAYLOAD -> pseudoRandomByteArrayWire(
                    request.seed(), payloadBytesForTarget(request));
            case HIGHLY_COMPRESSIBLE_ARRAY -> repeatedByteArrayWire(
                    request.seed(), exactByteArrayPayload(request.coordinate()));
            case COMPOUND_BREADTH -> compoundWire(
                    request.seed(), request.matrix()
                                    == P4E0ResearchMatrixRunner.Matrix.B_SINGLE_FILE
                            ? entriesForCompoundBytes(request.coordinate())
                            : coordinateInt(request));
            case LIST_BREADTH -> listWire(
                    request.seed(), request.matrix()
                                    == P4E0ResearchMatrixRunner.Matrix.B_SINGLE_FILE
                            ? elementsForListBytes(request.coordinate())
                            : coordinateInt(request));
            case LONG_STRINGS -> stringWire(request.seed(), request.coordinate());
            case UNRELATED_ATTACHMENT -> unrelatedAttachmentWire(
                    request.seed(), exactUnrelatedPayload(
                            request.seed(), request.coordinate()));
            case DEPTH -> depthWire(request.seed(), coordinateInt(request));
            case LONG_ARRAY -> longArrayWire(request.seed(), coordinateInt(request));
            default -> throw new ResearchGuardException("wire_profile");
        };
    }

    private static int payloadBytesForTarget(
            P4E0ResearchMatrixRunner.RunRequest request) throws ResearchGuardException {
        var target = request.coordinate();
        if (target <= 128L) {
            throw new ResearchGuardException("wire_target");
        }
        return Math.toIntExact(Math.min(Integer.MAX_VALUE - 16L, target - 128L));
    }

    private static int exactByteArrayPayload(long decompressedTarget)
            throws IOException {
        var base = countPayload(repeatedByteArrayWire(1L, 0));
        var payload = Math.subtractExact(decompressedTarget, base);
        if (payload < 0 || payload > Integer.MAX_VALUE) {
            throw new ResearchGuardException("decompressed_target");
        }
        return Math.toIntExact(payload);
    }

    private static int exactUnrelatedPayload(long seed, long decompressedTarget)
            throws IOException {
        var base = countPayload(unrelatedAttachmentWire(seed, 0));
        var payload = Math.subtractExact(decompressedTarget, base);
        if (payload < 0 || payload > Integer.MAX_VALUE) {
            throw new ResearchGuardException("unrelated_attachment_target");
        }
        return Math.toIntExact(payload);
    }

    private static long countPayload(P4E0ResearchWireNbt.PayloadWriter writer)
            throws IOException {
        var counter = new CountingOutput();
        try (var output = new DataOutputStream(counter)) {
            writer.write(output);
        }
        return counter.count;
    }

    private static P4E0ResearchWireNbt.PayloadWriter minimalWire(long seed, int witness) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            writeIntArrayField(output, "UUID", uuidInts(route(seed, witness)));
            writeIntField(output, "research_witness", witness);
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter pseudoRandomByteArrayWire(
            long seed, int length) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            writeIntArrayField(output, "UUID", uuidInts(route(seed, 0)));
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("research_payload");
            output.writeInt(length);
            writePseudoRandomBytes(output, length, seed);
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter repeatedByteArrayWire(
            long seed, int length) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            writeIntArrayField(output, "UUID", uuidInts(route(seed, 0)));
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("research_payload");
            output.writeInt(length);
            writeRepeated(output, length, 0x5a);
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter unrelatedAttachmentWire(
            long seed, int length) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            writeIntArrayField(output, "UUID", uuidInts(route(seed, 0)));
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF(AttachmentHolder.ATTACHMENTS_NBT_KEY);
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("other_mod:opaque");
            output.writeInt(length);
            writeRepeated(output, length, 0x5a);
            output.writeByte(Tag.TAG_END);
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter depthWire(long seed, int depth) {
        return output -> {
            if (depth < 1) {
                throw new IOException("depth fixture must include root");
            }
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            for (var level = 1; level < depth; level++) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("d");
            }
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF("leaf");
            output.writeUTF("synthetic");
            for (var level = 0; level < depth; level++) {
                output.writeByte(Tag.TAG_END);
            }
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter compoundWire(long seed, int entries) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            for (var index = 0; index < entries; index++) {
                writeIntField(output, String.format(
                        Locale.ROOT, "entry_%08d", index), index);
            }
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter listWire(long seed, int elements) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF("research_list");
            output.writeByte(Tag.TAG_INT);
            output.writeInt(elements);
            for (var index = 0; index < elements; index++) {
                output.writeInt(index);
            }
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter longArrayWire(long seed, int elements) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            output.writeByte(Tag.TAG_LONG_ARRAY);
            output.writeUTF("research_long_array");
            output.writeInt(elements);
            for (var index = 0; index < elements; index++) {
                output.writeLong(0x5034_4530_0000_0000L ^ index);
            }
            output.writeByte(Tag.TAG_END);
        };
    }

    private static P4E0ResearchWireNbt.PayloadWriter stringWire(long seed, long target) {
        return output -> {
            P4E0ResearchWireNbt.writeUnnamedCompoundStart(output);
            writeIntField(output, "DataVersion", CURRENT_DATA_VERSION);
            var remaining = Math.max(0L, target - 64L);
            var index = 0;
            var text = "s".repeat(60_000);
            while (remaining > 16L) {
                var valueBytes = (int) Math.min(60_000L, remaining - 16L);
                output.writeByte(Tag.TAG_STRING);
                output.writeUTF(String.format(Locale.ROOT, "string_%08d", index));
                output.writeUTF(text.substring(0, valueBytes));
                remaining -= valueBytes + 16L;
                index++;
            }
            output.writeByte(Tag.TAG_END);
        };
    }

    private static void writeIntField(DataOutput output, String name, int value)
            throws IOException {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF(name);
        output.writeInt(value);
    }

    private static void writeIntArrayField(DataOutput output, String name, int[] values)
            throws IOException {
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF(name);
        output.writeInt(values.length);
        for (var value : values) {
            output.writeInt(value);
        }
    }

    private static void writePseudoRandomBytes(
            DataOutput output, int count, long seed)
            throws IOException {
        var buffer = new byte[8_192];
        var random = new java.util.Random(seed ^ Integer.toUnsignedLong(count));
        var offset = 0;
        while (offset < count) {
            var length = Math.min(buffer.length, count - offset);
            random.nextBytes(buffer);
            output.write(buffer, 0, length);
            offset += length;
        }
    }

    private static void writeRepeated(DataOutput output, int count, int value)
            throws IOException {
        var buffer = new byte[8_192];
        java.util.Arrays.fill(buffer, (byte) value);
        var remaining = count;
        while (remaining > 0) {
            var length = Math.min(buffer.length, remaining);
            output.write(buffer, 0, length);
            remaining -= length;
        }
    }

    private static CompoundTag playerdata(long seed, int ordinal, Tag attachment) {
        var root = NbtUtils.addCurrentDataVersion(new CompoundTag());
        root.putUUID("UUID", UUID.fromString(route(seed, ordinal)));
        root.putInt("research_witness", ordinal);
        if (attachment != null) {
            var attachments = new CompoundTag();
            attachments.put("gramarye:player_skills", attachment.copy());
            root.put(AttachmentHolder.ATTACHMENTS_NBT_KEY, attachments);
        }
        return root;
    }

    private static void writePlayerdata(Path path, CompoundTag root) throws IOException {
        NbtIo.writeCompressed(root, path);
    }

    private static String route(long seed, int ordinal) {
        return new UUID(
                0x5034_4530_5232_0000L ^ seed,
                0x8000_0000_0000_0000L | Integer.toUnsignedLong(ordinal)).toString();
    }

    private static int[] uuidInts(String value) {
        var uuid = UUID.fromString(value);
        return new int[] {
            (int) (uuid.getMostSignificantBits() >>> 32),
            (int) uuid.getMostSignificantBits(),
            (int) (uuid.getLeastSignificantBits() >>> 32),
            (int) uuid.getLeastSignificantBits()
        };
    }

    private static int coordinateInt(P4E0ResearchMatrixRunner.RunRequest request)
            throws ResearchGuardException {
        try {
            return Math.toIntExact(request.coordinate());
        } catch (ArithmeticException exception) {
            throw new ResearchGuardException("coordinate_int_range");
        }
    }

    private static int entriesForCompoundBytes(long target)
            throws ResearchGuardException {
        // One fixed-width Int entry is 1 type + 2 name length + 14 name + 4 payload bytes.
        var entries = Math.max(1L, Math.floorDiv(Math.max(0L, target - 64L), 21L));
        if (entries > Integer.MAX_VALUE) {
            throw new ResearchGuardException("compound_target_range");
        }
        return Math.toIntExact(entries);
    }

    private static int elementsForListBytes(long target)
            throws ResearchGuardException {
        // Int list elements have no per-element type/name framing.
        var elements = Math.max(1L, Math.floorDiv(Math.max(0L, target - 64L), 4L));
        if (elements > Integer.MAX_VALUE) {
            throw new ResearchGuardException("list_target_range");
        }
        return Math.toIntExact(elements);
    }

    private static List<Path> regularFiles(Path root) throws IOException {
        try (var stream = Files.list(root)) {
            return stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static long treePhysicalBytes(Path root) throws IOException {
        return treeHash(root, regularFiles(root)).physicalBytes();
    }

    private static TreeHash treeHash(Path root, List<Path> files) throws IOException {
        var text = new StringBuilder();
        var bytes = 0L;
        for (var file : files) {
            var size = Files.size(file);
            bytes = Math.addExact(bytes, size);
            text.append(root.relativize(file).toString().replace('\\', '/'))
                    .append('|').append(size).append('|')
                    .append(P4E0ResearchHashing.sha256(file)).append('\n');
        }
        return new TreeHash(P4E0ResearchHashing.sha256(text.toString()), bytes);
    }

    private static Path requireFixtureRoot(Path root) {
        var normalized = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        var portable = normalized.toString().replace('\\', '/');
        if (!portable.contains("/build/p4-e0-research/matrix/")
                && !portable.contains("/build/p4-e0-research/combined-input/")) {
            throw new IllegalArgumentException("matrix fixture is outside its build tree");
        }
        return normalized;
    }

    private static Path requireCombinedRoot(Path root) {
        var normalized = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        var portable = normalized.toString().replace('\\', '/');
        if (!portable.contains("/build/p4-e0-research/combined-input/")) {
            throw new IllegalArgumentException(
                    "combined research input is outside its build tree");
        }
        return normalized;
    }

    private static void requireShape(boolean condition, String label) throws IOException {
        if (!condition) {
            throw new IOException(label + " shape changed");
        }
    }

    private static void requireHash(String hash) {
        if (hash == null || !hash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid fixture hash");
        }
    }

    private record TreeHash(String hash, long physicalBytes) {
    }

    private static final class CountingOutput extends OutputStream {
        private long count;

        @Override
        public void write(int value) {
            count = Math.addExact(count, 1L);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            count = Math.addExact(count, length);
        }
    }
}
