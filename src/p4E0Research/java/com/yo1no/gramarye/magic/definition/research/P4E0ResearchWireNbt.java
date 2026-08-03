package com.yo1no.gramarye.magic.definition.research;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.nbt.Tag;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Research-only deterministic gzip/NBT wire writer and strict streaming scanner.
 *
 * <p>The scanner exists because the materialized {@code CompoundTag} boundary has already lost
 * duplicate field occurrences. It is deliberately independent of the future P4-E parser and does
 * not establish a production safety ceiling.
 */
final class P4E0ResearchWireNbt {
    private static final int BUFFER_BYTES = 8_192;
    private static final int ROOT_FRAMING_BYTES = 3;

    private P4E0ResearchWireNbt() {
    }

    @FunctionalInterface
    interface PayloadWriter {
        /** Writes one complete unnamed Compound root, including type/name framing and End. */
        void write(DataOutput output) throws IOException;
    }

    record HeaderOptions(
            int extraBytes,
            int fileNameBytes,
            int commentBytes,
            boolean fhcrc,
            int repeatedByte) {
        HeaderOptions {
            if (extraBytes < 0 || extraBytes > 0xffff
                    || fileNameBytes < 0 || commentBytes < 0
                    || repeatedByte <= 0 || repeatedByte > 0xff) {
                throw new IllegalArgumentException("invalid research gzip header options");
            }
        }

        static HeaderOptions canonical() {
            return new HeaderOptions(0, 0, 0, false, 0x5a);
        }

        static HeaderOptions fileName(int bytes) {
            return new HeaderOptions(0, bytes, 0, true, 0x5a);
        }
    }

    record WriteFacts(
            long physicalBytes,
            long headerBytes,
            long decompressedBytes,
            String sha256) {
        WriteFacts {
            if (physicalBytes <= 0 || headerBytes < 10
                    || decompressedBytes < ROOT_FRAMING_BYTES) {
                throw new IllegalArgumentException("invalid research wire write facts");
            }
            if (sha256 != null && !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid research wire hash");
            }
        }
    }

    record ScanLimits(
            long compressedBytes,
            long decompressedBytes,
            long nodes,
            int containerDepth,
            long arrayElements,
            long modifiedUtf8Bytes) {
        ScanLimits(
                long compressedBytes,
                long decompressedBytes,
                long nodes,
                int containerDepth,
                long arrayElements) {
            this(
                    compressedBytes,
                    decompressedBytes,
                    nodes,
                    containerDepth,
                    arrayElements,
                    decompressedBytes);
        }

        ScanLimits {
            if (compressedBytes <= 0 || decompressedBytes <= 0 || nodes <= 0
                    || containerDepth <= 0 || arrayElements <= 0
                    || modifiedUtf8Bytes < 0
                    || compressedBytes == Long.MAX_VALUE
                    || decompressedBytes == Long.MAX_VALUE
                    || nodes == Long.MAX_VALUE
                    || arrayElements == Long.MAX_VALUE
                    || modifiedUtf8Bytes == Long.MAX_VALUE) {
                throw new IllegalArgumentException("research scan limits must be finite");
            }
        }
    }

    /**
     * R2Q checkpoint maxima for coordinates whose limit must be observed before payload
     * materialization. The per-file maxima and the aggregate maxima use the same shape so a
     * scanner cannot accidentally omit one side of a coordinate.
     */
    record CheckpointLimits(
            int containerDepth,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags) {
        CheckpointLimits {
            requireFiniteNonNegative(containerDepth, "container depth");
            requireFiniteNonNegative(compoundContainers, "compound containers");
            requireFiniteNonNegative(compoundFieldEntries, "compound field entries");
            requireFiniteNonNegative(listElements, "list elements");
            requireFiniteNonNegative(byteArrayElements, "byte-array elements");
            requireFiniteNonNegative(intArrayElements, "int-array elements");
            requireFiniteNonNegative(longArrayElements, "long-array elements");
            requireFiniteNonNegative(modifiedUtf8Bytes, "modified-UTF bytes");
            requireFiniteNonNegative(scalarTags, "scalar tags");
        }
    }

    /** Bounded counters published by the research checkpoint budget; no NBT is retained. */
    record CheckpointFacts(
            int maxContainerDepth,
            long compoundContainers,
            long compoundFieldEntries,
            long listElements,
            long byteArrayElements,
            long intArrayElements,
            long longArrayElements,
            long modifiedUtf8Bytes,
            long scalarTags) {
    }

    /** Aggregate checkpoint state deliberately shared by a bounded sequence of file scans. */
    static final class AggregateCheckpointBudget {
        private final CheckpointLimits maximum;
        private final long[] observed = new long[AdditiveCheckpoint.values().length];
        private int maxContainerDepth;

        AggregateCheckpointBudget(CheckpointLimits maximum) {
            this.maximum = Objects.requireNonNull(maximum, "maximum");
        }

        CheckpointLimits maximum() {
            return maximum;
        }

        CheckpointFacts observed() {
            return new CheckpointFacts(
                    maxContainerDepth,
                    observed(AdditiveCheckpoint.COMPOUND_CONTAINERS),
                    observed(AdditiveCheckpoint.COMPOUND_FIELD_ENTRIES),
                    observed(AdditiveCheckpoint.LIST_ELEMENTS),
                    observed(AdditiveCheckpoint.BYTE_ARRAY_ELEMENTS),
                    observed(AdditiveCheckpoint.INT_ARRAY_ELEMENTS),
                    observed(AdditiveCheckpoint.LONG_ARRAY_ELEMENTS),
                    observed(AdditiveCheckpoint.MODIFIED_UTF8_BYTES),
                    observed(AdditiveCheckpoint.SCALAR_TAGS));
        }

        private long observed(AdditiveCheckpoint coordinate) {
            return observed[coordinate.ordinal()];
        }
    }

    record ScanFacts(
            long physicalBytes,
            long decompressedBytes,
            P4E0ResearchNbtMetrics nbt,
            DataVersionFacts dataVersion,
            String sha256) {
        ScanFacts {
            Objects.requireNonNull(nbt, "nbt");
            Objects.requireNonNull(dataVersion, "dataVersion");
            if (physicalBytes <= 0 || decompressedBytes < ROOT_FRAMING_BYTES
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid research scan facts");
            }
        }
    }

    enum DataVersionKind {
        MISSING,
        INT_TAG,
        WRONG_TAG_TYPE
    }

    /** Bounded wire observation retained only until the post-structure DataVersion gate. */
    record DataVersionFacts(DataVersionKind kind, int intValue) {
        DataVersionFacts {
            Objects.requireNonNull(kind, "kind");
            if (kind != DataVersionKind.INT_TAG && intValue != 0) {
                throw new IllegalArgumentException("non-int DataVersion carries an int value");
            }
        }
    }

    static WriteFacts write(
            Path path,
            HeaderOptions header,
            int compressionLevel,
            long maximumPhysicalBytes,
            long maximumDecompressedBytes,
            PayloadWriter payload) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(header, "header");
        Objects.requireNonNull(payload, "payload");
        requireCompressionLevel(compressionLevel);
        requireFiniteMaximum(maximumPhysicalBytes, "physical");
        requireFiniteMaximum(maximumDecompressedBytes, "decompressed");
        var normalized = path.toAbsolutePath().normalize();
        var parent = normalized.getParent();
        if (parent == null) {
            throw new IOException("research wire path has no parent");
        }
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(normalized)) {
            throw new IOException("research wire output is a symbolic link");
        }

        WriteFacts facts;
        try (var raw = Files.newOutputStream(
                        normalized,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                var bounded = new BoundedOutput(raw, maximumPhysicalBytes)) {
            facts = writeMember(
                    bounded,
                    header,
                    compressionLevel,
                    maximumDecompressedBytes,
                    payload,
                    null);
        } catch (IOException | RuntimeException exception) {
            Files.deleteIfExists(normalized);
            throw exception;
        }
        return new WriteFacts(
                facts.physicalBytes(),
                facts.headerBytes(),
                facts.decompressedBytes(),
                P4E0ResearchHashing.sha256(normalized));
    }

    /** Measures the deterministic member without allocating an equally-sized byte array. */
    static WriteFacts measure(
            HeaderOptions header,
            int compressionLevel,
            long maximumPhysicalBytes,
            long maximumDecompressedBytes,
            PayloadWriter payload) throws IOException {
        var sink = new NullOutput();
        try (var bounded = new BoundedOutput(sink, maximumPhysicalBytes)) {
            return writeMember(
                    bounded,
                    header,
                    compressionLevel,
                    maximumDecompressedBytes,
                    payload,
                    null);
        }
    }

    static ScanFacts scan(Path path, ScanLimits limits) throws IOException {
        var checkpoints = legacyCheckpointLimits(limits);
        return scanInternal(
                path,
                limits,
                new PerFileCheckpointBudget(
                        checkpoints,
                        new AggregateCheckpointBudget(checkpoints),
                        null,
                        false),
                null,
                false);
    }

    static ScanFacts scan(
            Path path,
            ScanLimits limits,
            P4E0R2QModifiedUtf.AggregateBudget modifiedUtfAggregate)
            throws IOException {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(modifiedUtfAggregate, "modifiedUtfAggregate");
        var perFile = legacyCheckpointLimits(limits);
        var aggregateLimits = new CheckpointLimits(
                perFile.containerDepth(),
                perFile.compoundContainers(),
                perFile.compoundFieldEntries(),
                perFile.listElements(),
                perFile.byteArrayElements(),
                perFile.intArrayElements(),
                perFile.longArrayElements(),
                modifiedUtfAggregate.maximum(),
                perFile.scalarTags());
        return scanInternal(
                path,
                limits,
                new PerFileCheckpointBudget(
                        perFile,
                        new AggregateCheckpointBudget(aggregateLimits),
                        new P4E0R2QModifiedUtf.Budget(
                                limits.modifiedUtf8Bytes(), modifiedUtfAggregate),
                        false),
                null,
                false);
    }

    static ScanFacts scan(
            Path path,
            ScanLimits limits,
            CheckpointLimits perFileCheckpoints,
            AggregateCheckpointBudget aggregateCheckpoints)
            throws IOException {
        Objects.requireNonNull(perFileCheckpoints, "perFileCheckpoints");
        Objects.requireNonNull(aggregateCheckpoints, "aggregateCheckpoints");
        return scanInternal(
                path,
                limits,
                new PerFileCheckpointBudget(
                        perFileCheckpoints, aggregateCheckpoints, null, true),
                null,
                false);
    }

    /** Qualification-only entry point owned by the unified R2Q audit budget. */
    static ScanFacts scan(
            Path path,
            P4E0R2QAuditBudget budget,
            P4E0R2QAuditBudget.SourceSelection selection) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(selection, "selection");
        var attributes = Files.readAttributes(
                path.toAbsolutePath().normalize(),
                java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        var scope = budget.select(selection, attributes.size());
        var checkpoints = new PerFileCheckpointBudget(
                scope.checkpointLimits(),
                scope.aggregateCheckpoints(),
                null,
                true);
        try {
            return scanInternal(
                    path,
                    scope.scanLimits(),
                    checkpoints,
                    scope::observeDecompressed,
                    true);
        } catch (ResearchLimitException exception) {
            throw budget.translateStructuralFailure(
                    exception.coordinate(), checkpoints.eventStage());
        } catch (GzipFramingException exception) {
            throw budget.translateGzipFramingFailure();
        } catch (FixtureParserException exception) {
            throw budget.translateFixtureParserFailure(exception.stage());
        }
    }

    private static ScanFacts scanInternal(
            Path path,
            ScanLimits limits,
            PerFileCheckpointBudget checkpoints,
            InputObserver decompressedObserver,
            boolean classifyExpectedFailures)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(checkpoints, "checkpoints");
        var normalized = path.toAbsolutePath().normalize();
        var attributes = Files.readAttributes(
                normalized,
                java.nio.file.attribute.BasicFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()
                || attributes.fileKey() == null) {
            throw new IOException("research wire input identity is unsupported");
        }
        if (attributes.size() > limits.compressedBytes()) {
            throw new ResearchLimitException("compressed_bytes");
        }

        try (var raw = new BoundedInput(
                        Files.newInputStream(normalized, StandardOpenOption.READ),
                        limits.compressedBytes());
                var callerBuffer = new BufferedInputStream(raw, BUFFER_BYTES);
                var gzip = openGzip(callerBuffer, classifyExpectedFailures);
                var decompressed = new BoundedInput(
                        gzip, limits.decompressedBytes(), decompressedObserver);
                var data = new DataInputStream(new BufferedInputStream(
                        decompressed, BUFFER_BYTES))) {
            final ScannedRoot root;
            try {
                var scanner = new StrictScanner(
                        data,
                        limits,
                        checkpoints);
                root = scanner.readUnnamedCompound();
                checkpoints.enter(P4E0R2QCasePlan.FailureStage.DATA_VERSION);
                if (data.read() != -1) {
                    throw new IOException("research NBT has decompressed trailing data");
                }
            } catch (ResearchLimitException
                    | P4E0R2QAuditBudget.AuditFailure
                    | GzipFramingException
                    | FixtureParserException exception) {
                throw exception;
            } catch (IOException exception) {
                if (classifyExpectedFailures) {
                    throw new FixtureParserException(checkpoints.eventStage());
                }
                throw exception;
            }
            try {
                if (callerBuffer.read() != -1) {
                    throw new IOException("research gzip has a second member or trailing data");
                }
                if (!raw.actualEofObserved()) {
                    throw new IOException("research gzip physical EOF was not observed");
                }
            } catch (ResearchLimitException | GzipFramingException exception) {
                throw exception;
            } catch (IOException exception) {
                if (classifyExpectedFailures) {
                    throw new GzipFramingException();
                }
                throw exception;
            }
            var after = Files.readAttributes(
                    normalized,
                    java.nio.file.attribute.BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.fileKey().equals(after.fileKey())
                    || attributes.size() != after.size()
                    || !attributes.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw new IOException("research wire identity changed during scan");
            }
            return new ScanFacts(
                    attributes.size(),
                    decompressed.count(),
                    root.metrics(),
                    root.dataVersion(),
                    P4E0ResearchHashing.sha256(normalized));
        }
    }

    private static InputStream openGzip(
            BufferedInputStream callerBuffer, boolean classifyExpectedFailures)
            throws IOException {
        try {
            var gzip = new GzipCompressorInputStream(callerBuffer, false);
            return classifyExpectedFailures
                    ? new GzipFailureClassifyingInput(gzip)
                    : gzip;
        } catch (IOException exception) {
            if (classifyExpectedFailures) {
                throw new GzipFramingException();
            }
            throw exception;
        }
    }

    /** Negative-control helper used by tests to prove duplicate fields are rejected pre-map. */
    static void writeDuplicateFieldProbe(Path path, long physicalMaximum) throws IOException {
        write(
                path,
                HeaderOptions.canonical(),
                Deflater.DEFAULT_COMPRESSION,
                physicalMaximum,
                4_096L,
                output -> {
                    writeUnnamedCompoundStart(output);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("duplicate");
                    output.writeInt(1);
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF("duplicate");
                    output.writeInt(2);
                    output.writeByte(Tag.TAG_END);
                });
    }

    static void writeUnnamedCompoundStart(DataOutput output) throws IOException {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeShort(0);
    }

    private static WriteFacts writeMember(
            BoundedOutput output,
            HeaderOptions header,
            int compressionLevel,
            long maximumDecompressedBytes,
            PayloadWriter payload,
            String ignoredHash) throws IOException {
        var headerOutput = new HeaderOutput(output);
        writeHeader(headerOutput, header, compressionLevel);
        var headerBytes = output.count();
        var bodyCrc = new CRC32();
        var deflater = new Deflater(compressionLevel, true);
        final long decompressedBytes;
        try {
            var compressed = new DeflaterOutputStream(
                    output, deflater, BUFFER_BYTES, false);
            var body = new PayloadOutput(
                    compressed, bodyCrc, maximumDecompressedBytes);
            var data = new DataOutputStream(body);
            payload.write(data);
            data.flush();
            compressed.finish();
            decompressedBytes = body.count();
        } finally {
            deflater.end();
        }
        writeLittleEndianInt(output, bodyCrc.getValue());
        writeLittleEndianInt(output, decompressedBytes & 0xffff_ffffL);
        output.flush();
        return new WriteFacts(
                output.count(), headerBytes, decompressedBytes, ignoredHash);
    }

    private static void writeHeader(
            HeaderOutput output, HeaderOptions options, int compressionLevel)
            throws IOException {
        var flags = (options.fhcrc() ? 0x02 : 0)
                | (options.extraBytes() > 0 ? 0x04 : 0)
                | (options.fileNameBytes() > 0 ? 0x08 : 0)
                | (options.commentBytes() > 0 ? 0x10 : 0);
        output.write(0x1f);
        output.write(0x8b);
        output.write(8);
        output.write(flags);
        for (var index = 0; index < 4; index++) {
            output.write(0);
        }
        output.write(compressionLevel == Deflater.BEST_COMPRESSION ? 2
                : compressionLevel == Deflater.BEST_SPEED ? 4 : 0);
        output.write(255);
        if (options.extraBytes() > 0) {
            output.write(options.extraBytes() & 0xff);
            output.write(options.extraBytes() >>> 8);
            output.writeRepeated(options.repeatedByte(), options.extraBytes());
        }
        if (options.fileNameBytes() > 0) {
            output.writeRepeated(options.repeatedByte(), options.fileNameBytes());
            output.write(0);
        }
        if (options.commentBytes() > 0) {
            output.writeRepeated(options.repeatedByte(), options.commentBytes());
            output.write(0);
        }
        if (options.fhcrc()) {
            var low = (int) output.crcValue() & 0xffff;
            output.writeWithoutCrc(low & 0xff);
            output.writeWithoutCrc(low >>> 8);
        }
    }

    private static void writeLittleEndianInt(OutputStream output, long value)
            throws IOException {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
    }

    private static void requireCompressionLevel(int level) {
        if (level < Deflater.DEFAULT_COMPRESSION || level > Deflater.BEST_COMPRESSION) {
            throw new IllegalArgumentException("invalid research compression level");
        }
    }

    private static void requireFiniteMaximum(long value, String label) {
        if (value <= 0 || value == Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " maximum must be finite");
        }
    }

    private static void requireFiniteNonNegative(long value, String label) {
        if (value < 0 || value == Long.MAX_VALUE) {
            throw new IllegalArgumentException(label + " maximum must be finite");
        }
    }

    private static CheckpointLimits legacyCheckpointLimits(ScanLimits limits) {
        Objects.requireNonNull(limits, "limits");
        return new CheckpointLimits(
                limits.containerDepth(),
                limits.nodes(),
                limits.nodes(),
                limits.nodes(),
                limits.arrayElements(),
                limits.arrayElements(),
                limits.arrayElements(),
                limits.modifiedUtf8Bytes(),
                limits.nodes());
    }

    static final class ResearchLimitException extends IOException {
        private final String coordinate;

        ResearchLimitException(String coordinate) {
            super("research observation limit reached");
            this.coordinate = Objects.requireNonNull(coordinate, "coordinate");
        }

        String coordinate() {
            return coordinate;
        }
    }

    /** Fixed, cause-free qualification classification for expected gzip framing failures. */
    private static final class GzipFramingException extends IOException {
        private GzipFramingException() {
            super("research gzip framing rejected");
        }
    }

    /** Fixed, cause-free qualification classification for expected wire-fixture failures. */
    private static final class FixtureParserException extends IOException {
        private final P4E0R2QCasePlan.FailureStage stage;

        private FixtureParserException(P4E0R2QCasePlan.FailureStage stage) {
            super("research wire fixture rejected");
            this.stage = Objects.requireNonNull(stage, "stage");
        }

        private P4E0R2QCasePlan.FailureStage stage() {
            return stage;
        }
    }

    /**
     * Keeps Commons framing failures distinct from parser failures without retaining its exception
     * object. Runtime exceptions and Errors deliberately pass through untouched.
     */
    private static final class GzipFailureClassifyingInput extends InputStream {
        private final InputStream source;

        private GzipFailureClassifyingInput(InputStream source) {
            this.source = Objects.requireNonNull(source, "source");
        }

        @Override
        public int read() throws IOException {
            try {
                return source.read();
            } catch (ResearchLimitException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new GzipFramingException();
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            try {
                return source.read(bytes, offset, length);
            } catch (ResearchLimitException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new GzipFramingException();
            }
        }

        @Override
        public void close() throws IOException {
            try {
                source.close();
            } catch (ResearchLimitException exception) {
                throw exception;
            } catch (IOException exception) {
                throw new GzipFramingException();
            }
        }
    }

    static final class DuplicateNbtFieldException extends IOException {
        DuplicateNbtFieldException() {
            super("duplicate NBT field rejected");
        }
    }

    private static final class StrictScanner {
        private final DataInputStream input;
        private final ScanLimits limits;
        private final PerFileCheckpointBudget checkpoints;
        private final MutableMetrics metrics = new MutableMetrics();
        private final MutableDataVersion dataVersion = new MutableDataVersion();

        private StrictScanner(
                DataInputStream input,
                ScanLimits limits,
                PerFileCheckpointBudget checkpoints) {
            this.input = input;
            this.limits = limits;
            this.checkpoints = checkpoints;
        }

        private ScannedRoot readUnnamedCompound() throws IOException {
            checkpoints.enter(
                    P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND);
            if (input.readUnsignedByte() != Tag.TAG_COMPOUND
                    || input.readUnsignedShort() != 0) {
                throw new IOException("research NBT root is not unnamed Compound");
            }
            metrics.node(limits);
            readCompound(1);
            return new ScannedRoot(metrics.freeze(), dataVersion.freeze());
        }

        private void readPayload(int type, int parentDepth) throws IOException {
            checkpoints.enter(
                    P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND);
            switch (type) {
                case Tag.TAG_BYTE -> {
                    checkpoints.observeScalar();
                    input.readByte();
                    metrics.scalar();
                }
                case Tag.TAG_SHORT -> {
                    checkpoints.observeScalar();
                    input.readShort();
                    metrics.scalar();
                }
                case Tag.TAG_INT -> {
                    checkpoints.observeScalar();
                    input.readInt();
                    metrics.scalar();
                }
                case Tag.TAG_LONG -> {
                    checkpoints.observeScalar();
                    input.readLong();
                    metrics.scalar();
                }
                case Tag.TAG_FLOAT -> {
                    checkpoints.observeScalar();
                    input.readFloat();
                    metrics.scalar();
                }
                case Tag.TAG_DOUBLE -> {
                    checkpoints.observeScalar();
                    input.readDouble();
                    metrics.scalar();
                }
                case Tag.TAG_BYTE_ARRAY -> readByteArray();
                case Tag.TAG_STRING -> {
                    checkpoints.observeScalar();
                    checkpoints.enter(P4E0R2QCasePlan.FailureStage.MODIFIED_UTF_PREFIX);
                    P4E0R2QModifiedUtf.read(input, this::observeModifiedUtfPrefix);
                    metrics.string();
                }
                case Tag.TAG_LIST -> readList(Math.addExact(parentDepth, 1));
                case Tag.TAG_COMPOUND -> readCompound(Math.addExact(parentDepth, 1));
                case Tag.TAG_INT_ARRAY -> readIntArray();
                case Tag.TAG_LONG_ARRAY -> readLongArray();
                default -> throw new IOException("unknown NBT tag type");
            }
        }

        private void readCompound(int depth) throws IOException {
            checkpoints.enter(
                    P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND);
            checkpoints.observeContainerDepth(depth);
            checkpoints.observe(AdditiveCheckpoint.COMPOUND_CONTAINERS, 1L);
            metrics.containerDepth(depth, limits);
            metrics.compoundCount = Math.addExact(metrics.compoundCount, 1L);
            Set<String> fields = new HashSet<>();
            while (true) {
                var type = input.readUnsignedByte();
                if (type == Tag.TAG_END) {
                    return;
                }
                checkpoints.enter(
                        P4E0R2QCasePlan.FailureStage.COMPOUND_FIELD_CHECKPOINT);
                checkpoints.observe(AdditiveCheckpoint.COMPOUND_FIELD_ENTRIES, 1L);
                checkpoints.enter(P4E0R2QCasePlan.FailureStage.MODIFIED_UTF_PREFIX);
                var name = P4E0R2QModifiedUtf.read(
                        input, this::observeModifiedUtfPrefix);
                if (!fields.add(name)) {
                    throw new DuplicateNbtFieldException();
                }
                metrics.compoundEntry();
                metrics.node(limits);
                if (depth == 1 && name.equals("DataVersion")) {
                    readRootDataVersion(type, depth);
                } else {
                    readPayload(type, depth);
                }
            }
        }

        private void readRootDataVersion(int type, int depth) throws IOException {
            if (type == Tag.TAG_INT) {
                checkpoints.observeScalar();
                checkpoints.enter(P4E0R2QCasePlan.FailureStage.DATA_VERSION);
                dataVersion.observeInt(input.readInt());
                metrics.scalar();
                return;
            }
            dataVersion.observeWrongType();
            readPayload(type, depth);
        }

        private void readList(int depth) throws IOException {
            checkpoints.enter(
                    P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND);
            checkpoints.observeContainerDepth(depth);
            metrics.containerDepth(depth, limits);
            metrics.listCount = Math.addExact(metrics.listCount, 1L);
            checkpoints.enter(P4E0R2QCasePlan.FailureStage.LIST_LENGTH);
            var type = input.readUnsignedByte();
            var length = readNonNegativeLength();
            if (length > 0 && type == Tag.TAG_END) {
                throw new IOException("nonempty NBT list has End element type");
            }
            checkpoints.observe(AdditiveCheckpoint.LIST_ELEMENTS, length);
            metrics.listElements = Math.addExact(metrics.listElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
            for (var index = 0; index < length; index++) {
                metrics.node(limits);
                readPayload(type, depth);
            }
        }

        private void readByteArray() throws IOException {
            checkpoints.enter(P4E0R2QCasePlan.FailureStage.TYPED_ARRAY_LENGTH);
            var length = readArrayLength();
            checkpoints.observe(AdditiveCheckpoint.BYTE_ARRAY_ELEMENTS, length);
            skipExact(length);
            metrics.byteArrays = Math.addExact(metrics.byteArrays, 1L);
            metrics.byteArrayElements = Math.addExact(metrics.byteArrayElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
        }

        private void readIntArray() throws IOException {
            checkpoints.enter(P4E0R2QCasePlan.FailureStage.TYPED_ARRAY_LENGTH);
            var length = readArrayLength();
            checkpoints.observe(AdditiveCheckpoint.INT_ARRAY_ELEMENTS, length);
            for (var index = 0; index < length; index++) {
                input.readInt();
            }
            metrics.intArrays = Math.addExact(metrics.intArrays, 1L);
            metrics.intArrayElements = Math.addExact(metrics.intArrayElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
        }

        private void readLongArray() throws IOException {
            checkpoints.enter(P4E0R2QCasePlan.FailureStage.TYPED_ARRAY_LENGTH);
            var length = readArrayLength();
            checkpoints.observe(AdditiveCheckpoint.LONG_ARRAY_ELEMENTS, length);
            for (var index = 0; index < length; index++) {
                input.readLong();
            }
            metrics.longArrays = Math.addExact(metrics.longArrays, 1L);
            metrics.longArrayElements = Math.addExact(metrics.longArrayElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
        }

        private int readNonNegativeLength() throws IOException {
            var length = input.readInt();
            if (length < 0) {
                throw new IOException("negative NBT collection length");
            }
            return length;
        }

        private int readArrayLength() throws IOException {
            var length = readNonNegativeLength();
            if (!checkpoints.typedArrayCoordinates()
                    && (long) length > limits.arrayElements()) {
                throw new ResearchLimitException("array_elements");
            }
            return length;
        }

        private void skipExact(int bytes) throws IOException {
            var remaining = bytes;
            var buffer = new byte[Math.min(BUFFER_BYTES, Math.max(1, bytes))];
            while (remaining > 0) {
                var read = input.read(buffer, 0, Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("truncated NBT array");
                }
                remaining -= read;
            }
        }

        private void observeModifiedUtfPrefix(int encodedBytes) throws IOException {
            checkpoints.enter(P4E0R2QCasePlan.FailureStage.MODIFIED_UTF_PREFIX);
            checkpoints.observeModifiedUtf(encodedBytes);
            metrics.modifiedUtfPrefix(encodedBytes);
        }
    }

    private record ScannedRoot(
            P4E0ResearchNbtMetrics metrics, DataVersionFacts dataVersion) {
        private ScannedRoot {
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(dataVersion, "dataVersion");
        }
    }

    private static final class MutableDataVersion {
        private DataVersionKind kind = DataVersionKind.MISSING;
        private int intValue;

        private void observeInt(int value) {
            kind = DataVersionKind.INT_TAG;
            intValue = value;
        }

        private void observeWrongType() {
            kind = DataVersionKind.WRONG_TAG_TYPE;
            intValue = 0;
        }

        private DataVersionFacts freeze() {
            return new DataVersionFacts(kind, intValue);
        }
    }

    private enum AdditiveCheckpoint {
        COMPOUND_CONTAINERS(
                "compound_containers_per_file", "compound_containers_total"),
        COMPOUND_FIELD_ENTRIES(
                "compound_field_entries_per_file", "compound_field_entries_total"),
        LIST_ELEMENTS("list_elements_per_file", "list_elements_total"),
        BYTE_ARRAY_ELEMENTS(
                "byte_array_elements_per_file", "byte_array_elements_total"),
        INT_ARRAY_ELEMENTS(
                "int_array_elements_per_file", "int_array_elements_total"),
        LONG_ARRAY_ELEMENTS(
                "long_array_elements_per_file", "long_array_elements_total"),
        MODIFIED_UTF8_BYTES(
                "modified_utf8_bytes_per_file", "modified_utf8_bytes_total"),
        SCALAR_TAGS("scalar_tags_per_file", "scalar_tags_total");

        private final String perFileCoordinate;
        private final String aggregateCoordinate;

        AdditiveCheckpoint(String perFileCoordinate, String aggregateCoordinate) {
            this.perFileCoordinate = perFileCoordinate;
            this.aggregateCoordinate = aggregateCoordinate;
        }
    }

    /** Atomic per-file/aggregate checkpoint state for one scan. */
    private static final class PerFileCheckpointBudget {
        private final CheckpointLimits maximum;
        private final AggregateCheckpointBudget aggregate;
        private final P4E0R2QModifiedUtf.Budget legacyModifiedUtf;
        private final boolean typedArrayCoordinates;
        private final long[] observed = new long[AdditiveCheckpoint.values().length];
        private int maxContainerDepth;
        private P4E0R2QCasePlan.FailureStage eventStage =
                P4E0R2QCasePlan.FailureStage.DEPTH_CONTAINER_SCALAR_KIND;

        private PerFileCheckpointBudget(
                CheckpointLimits maximum,
                AggregateCheckpointBudget aggregate,
                P4E0R2QModifiedUtf.Budget legacyModifiedUtf,
                boolean typedArrayCoordinates) {
            this.maximum = Objects.requireNonNull(maximum, "maximum");
            this.aggregate = Objects.requireNonNull(aggregate, "aggregate");
            this.legacyModifiedUtf = legacyModifiedUtf;
            this.typedArrayCoordinates = typedArrayCoordinates;
        }

        private boolean typedArrayCoordinates() {
            return typedArrayCoordinates;
        }

        private void enter(P4E0R2QCasePlan.FailureStage stage) {
            eventStage = Objects.requireNonNull(stage, "stage");
        }

        private P4E0R2QCasePlan.FailureStage eventStage() {
            return eventStage;
        }

        private void observeContainerDepth(int depth) throws ResearchLimitException {
            if (depth > maximum.containerDepth()) {
                throw new ResearchLimitException("container_depth_per_file");
            }
            if (depth > aggregate.maximum.containerDepth()) {
                throw new ResearchLimitException("container_depth_aggregate");
            }
            maxContainerDepth = Math.max(maxContainerDepth, depth);
            aggregate.maxContainerDepth = Math.max(aggregate.maxContainerDepth, depth);
        }

        private void observeScalar() throws ResearchLimitException {
            observe(AdditiveCheckpoint.SCALAR_TAGS, 1L);
        }

        private void observeModifiedUtf(int encodedBytes) throws IOException {
            if (legacyModifiedUtf != null) {
                legacyModifiedUtf.observe(encodedBytes);
            }
            observe(AdditiveCheckpoint.MODIFIED_UTF8_BYTES, encodedBytes);
        }

        private void observe(AdditiveCheckpoint coordinate, long increment)
                throws ResearchLimitException {
            if (increment < 0) {
                throw new IllegalArgumentException("negative research checkpoint increment");
            }
            var index = coordinate.ordinal();
            var nextPerFile = checkedNext(
                    observed[index],
                    increment,
                    maximum(maximum, coordinate),
                    coordinate.perFileCoordinate);
            var nextAggregate = checkedNext(
                    aggregate.observed[index],
                    increment,
                    maximum(aggregate.maximum, coordinate),
                    coordinate.aggregateCoordinate);
            observed[index] = nextPerFile;
            aggregate.observed[index] = nextAggregate;
        }

        private static long checkedNext(
                long current, long increment, long maximum, String coordinate)
                throws ResearchLimitException {
            final long next;
            try {
                next = Math.addExact(current, increment);
            } catch (ArithmeticException exception) {
                throw new ResearchLimitException(coordinate);
            }
            if (next > maximum) {
                throw new ResearchLimitException(coordinate);
            }
            return next;
        }

        private static long maximum(
                CheckpointLimits limits, AdditiveCheckpoint coordinate) {
            return switch (coordinate) {
                case COMPOUND_CONTAINERS -> limits.compoundContainers();
                case COMPOUND_FIELD_ENTRIES -> limits.compoundFieldEntries();
                case LIST_ELEMENTS -> limits.listElements();
                case BYTE_ARRAY_ELEMENTS -> limits.byteArrayElements();
                case INT_ARRAY_ELEMENTS -> limits.intArrayElements();
                case LONG_ARRAY_ELEMENTS -> limits.longArrayElements();
                case MODIFIED_UTF8_BYTES -> limits.modifiedUtf8Bytes();
                case SCALAR_TAGS -> limits.scalarTags();
            };
        }
    }

    private static final class MutableMetrics {
        private long maxDepth;
        private long compoundCount;
        private long compoundEntries;
        private long listCount;
        private long listElements;
        private long scalarCount;
        private long byteArrays;
        private long byteArrayElements;
        private long intArrays;
        private long intArrayElements;
        private long longArrays;
        private long longArrayElements;
        private long stringCount;
        private long modifiedUtf8Bytes;
        private long nodes;
        private long valueElements;

        private void node(ScanLimits limits) throws ResearchLimitException {
            nodes = Math.addExact(nodes, 1L);
            if (nodes > limits.nodes()) {
                throw new ResearchLimitException("nbt_nodes");
            }
        }

        private void containerDepth(int depth, ScanLimits limits)
                throws ResearchLimitException {
            if (depth > limits.containerDepth()) {
                throw new ResearchLimitException("container_depth");
            }
            maxDepth = Math.max(maxDepth, depth);
        }

        private void compoundEntry() {
            compoundEntries = Math.addExact(compoundEntries, 1L);
            valueElements = Math.addExact(valueElements, 1L);
        }

        private void scalar() {
            scalarCount = Math.addExact(scalarCount, 1L);
        }

        private void string() {
            scalar();
            stringCount = Math.addExact(stringCount, 1L);
        }

        private void modifiedUtfPrefix(int encodedBytes) {
            modifiedUtf8Bytes = Math.addExact(
                    modifiedUtf8Bytes, encodedBytes);
        }

        private P4E0ResearchNbtMetrics freeze() {
            return new P4E0ResearchNbtMetrics(
                    maxDepth,
                    compoundCount,
                    compoundEntries,
                    listCount,
                    listElements,
                    scalarCount,
                    byteArrays,
                    byteArrayElements,
                    intArrays,
                    intArrayElements,
                    longArrays,
                    longArrayElements,
                    stringCount,
                    modifiedUtf8Bytes,
                    nodes,
                    valueElements);
        }
    }

    private static final class HeaderOutput {
        private final OutputStream output;
        private final CRC32 crc = new CRC32();
        private final byte[] buffer = new byte[BUFFER_BYTES];

        private HeaderOutput(OutputStream output) {
            this.output = output;
        }

        private void write(int value) throws IOException {
            output.write(value);
            crc.update(value);
        }

        private void writeWithoutCrc(int value) throws IOException {
            output.write(value);
        }

        private void writeRepeated(int value, int count) throws IOException {
            java.util.Arrays.fill(buffer, (byte) value);
            var remaining = count;
            while (remaining > 0) {
                var length = Math.min(remaining, buffer.length);
                output.write(buffer, 0, length);
                crc.update(buffer, 0, length);
                remaining -= length;
            }
        }

        private long crcValue() {
            return crc.getValue();
        }
    }

    private static final class PayloadOutput extends OutputStream {
        private final OutputStream delegate;
        private final CRC32 crc;
        private final long observationLimit;
        private long count;

        private PayloadOutput(OutputStream delegate, CRC32 crc, long maximum) {
            this.delegate = delegate;
            this.crc = crc;
            this.observationLimit = Math.addExact(maximum, 1L);
        }

        @Override
        public void write(int value) throws IOException {
            if (count == observationLimit) {
                throw new ResearchLimitException("decompressed_bytes");
            }
            delegate.write(value);
            crc.update(value);
            count = Math.addExact(count, 1L);
            if (count == observationLimit) {
                throw new ResearchLimitException("decompressed_bytes");
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            var permitted = (int) Math.min((long) length, observationLimit - count);
            if (permitted > 0) {
                delegate.write(bytes, offset, permitted);
                crc.update(bytes, offset, permitted);
                count = Math.addExact(count, permitted);
            }
            if (permitted != length || count == observationLimit) {
                throw new ResearchLimitException("decompressed_bytes");
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        private long count() {
            return count;
        }
    }

    private static final class BoundedOutput extends OutputStream {
        private final OutputStream delegate;
        private final long observationLimit;
        private long count;

        private BoundedOutput(OutputStream delegate, long maximum) {
            this.delegate = delegate;
            this.observationLimit = Math.addExact(maximum, 1L);
        }

        @Override
        public void write(int value) throws IOException {
            if (count == observationLimit) {
                throw new ResearchLimitException("compressed_bytes");
            }
            delegate.write(value);
            count = Math.addExact(count, 1L);
            if (count == observationLimit) {
                throw new ResearchLimitException("compressed_bytes");
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            var permitted = (int) Math.min((long) length, observationLimit - count);
            if (permitted > 0) {
                delegate.write(bytes, offset, permitted);
                count = Math.addExact(count, permitted);
            }
            if (permitted != length || count == observationLimit) {
                throw new ResearchLimitException("compressed_bytes");
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long count() {
            return count;
        }
    }

    private static final class BoundedInput extends InputStream {
        private final InputStream delegate;
        private final long observationLimit;
        private final InputObserver observer;
        private long count;
        private boolean actualEofObserved;

        private BoundedInput(InputStream delegate, long maximum) {
            this(delegate, maximum, null);
        }

        private BoundedInput(
                InputStream delegate, long maximum, InputObserver observer) {
            this.delegate = delegate;
            this.observationLimit = Math.addExact(maximum, 1L);
            this.observer = observer;
        }

        @Override
        public int read() throws IOException {
            var one = new byte[1];
            return read(one, 0, 1) < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (count == observationLimit) {
                throw new ResearchLimitException("input_bytes");
            }
            var permitted = (int) Math.min((long) length, observationLimit - count);
            var read = delegate.read(bytes, offset, permitted);
            if (read < 0) {
                actualEofObserved = true;
                return -1;
            }
            if (observer != null) {
                observer.observe(read);
            }
            count = Math.addExact(count, read);
            if (count == observationLimit) {
                throw new ResearchLimitException("input_bytes");
            }
            return read;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private long count() {
            return count;
        }

        private boolean actualEofObserved() {
            return actualEofObserved;
        }
    }

    @FunctionalInterface
    private interface InputObserver {
        void observe(long bytes) throws IOException;
    }

    private static final class NullOutput extends OutputStream {
        @Override
        public void write(int value) {
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            Objects.checkFromIndexSize(offset, length, bytes.length);
        }
    }
}
