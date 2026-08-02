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
            long arrayElements) {
        ScanLimits {
            if (compressedBytes <= 0 || decompressedBytes <= 0 || nodes <= 0
                    || containerDepth <= 0 || arrayElements <= 0
                    || compressedBytes == Long.MAX_VALUE
                    || decompressedBytes == Long.MAX_VALUE
                    || nodes == Long.MAX_VALUE
                    || arrayElements == Long.MAX_VALUE) {
                throw new IllegalArgumentException("research scan limits must be finite");
            }
        }
    }

    record ScanFacts(
            long physicalBytes,
            long decompressedBytes,
            P4E0ResearchNbtMetrics nbt,
            String sha256) {
        ScanFacts {
            Objects.requireNonNull(nbt, "nbt");
            if (physicalBytes <= 0 || decompressedBytes < ROOT_FRAMING_BYTES
                    || sha256 == null || !sha256.matches("[0-9a-f]{64}")) {
                throw new IllegalArgumentException("invalid research scan facts");
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
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(limits, "limits");
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
                var gzip = new GzipCompressorInputStream(callerBuffer, false);
                var decompressed = new BoundedInput(gzip, limits.decompressedBytes());
                var data = new DataInputStream(new BufferedInputStream(
                        decompressed, BUFFER_BYTES))) {
            var scanner = new StrictScanner(data, limits);
            var metrics = scanner.readUnnamedCompound();
            if (data.read() != -1) {
                throw new IOException("research NBT has decompressed trailing data");
            }
            if (callerBuffer.read() != -1) {
                throw new IOException("research gzip has a second member or trailing data");
            }
            if (!raw.actualEofObserved()) {
                throw new IOException("research gzip physical EOF was not observed");
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
                    metrics,
                    P4E0ResearchHashing.sha256(normalized));
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

    static final class DuplicateNbtFieldException extends IOException {
        DuplicateNbtFieldException() {
            super("duplicate NBT field rejected");
        }
    }

    private static final class StrictScanner {
        private final DataInputStream input;
        private final ScanLimits limits;
        private final MutableMetrics metrics = new MutableMetrics();

        private StrictScanner(DataInputStream input, ScanLimits limits) {
            this.input = input;
            this.limits = limits;
        }

        private P4E0ResearchNbtMetrics readUnnamedCompound() throws IOException {
            if (input.readUnsignedByte() != Tag.TAG_COMPOUND
                    || input.readUnsignedShort() != 0) {
                throw new IOException("research NBT root is not unnamed Compound");
            }
            metrics.node(limits);
            readCompound(1);
            return metrics.freeze();
        }

        private void readPayload(int type, int parentDepth) throws IOException {
            switch (type) {
                case Tag.TAG_BYTE -> {
                    input.readByte();
                    metrics.scalar();
                }
                case Tag.TAG_SHORT -> {
                    input.readShort();
                    metrics.scalar();
                }
                case Tag.TAG_INT -> {
                    input.readInt();
                    metrics.scalar();
                }
                case Tag.TAG_LONG -> {
                    input.readLong();
                    metrics.scalar();
                }
                case Tag.TAG_FLOAT -> {
                    input.readFloat();
                    metrics.scalar();
                }
                case Tag.TAG_DOUBLE -> {
                    input.readDouble();
                    metrics.scalar();
                }
                case Tag.TAG_BYTE_ARRAY -> readByteArray();
                case Tag.TAG_STRING -> {
                    var value = input.readUTF();
                    metrics.string(value);
                }
                case Tag.TAG_LIST -> readList(Math.addExact(parentDepth, 1));
                case Tag.TAG_COMPOUND -> readCompound(Math.addExact(parentDepth, 1));
                case Tag.TAG_INT_ARRAY -> readIntArray();
                case Tag.TAG_LONG_ARRAY -> readLongArray();
                default -> throw new IOException("unknown NBT tag type");
            }
        }

        private void readCompound(int depth) throws IOException {
            metrics.containerDepth(depth, limits);
            metrics.compoundCount = Math.addExact(metrics.compoundCount, 1L);
            Set<String> fields = new HashSet<>();
            while (true) {
                var type = input.readUnsignedByte();
                if (type == Tag.TAG_END) {
                    return;
                }
                var name = input.readUTF();
                if (!fields.add(name)) {
                    throw new DuplicateNbtFieldException();
                }
                metrics.compoundEntry(name);
                metrics.node(limits);
                readPayload(type, depth);
            }
        }

        private void readList(int depth) throws IOException {
            metrics.containerDepth(depth, limits);
            metrics.listCount = Math.addExact(metrics.listCount, 1L);
            var type = input.readUnsignedByte();
            var length = readNonNegativeLength();
            if (length > 0 && type == Tag.TAG_END) {
                throw new IOException("nonempty NBT list has End element type");
            }
            metrics.listElements = Math.addExact(metrics.listElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
            for (var index = 0; index < length; index++) {
                metrics.node(limits);
                readPayload(type, depth);
            }
        }

        private void readByteArray() throws IOException {
            var length = readArrayLength();
            skipExact(length);
            metrics.byteArrays = Math.addExact(metrics.byteArrays, 1L);
            metrics.byteArrayElements = Math.addExact(metrics.byteArrayElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
        }

        private void readIntArray() throws IOException {
            var length = readArrayLength();
            for (var index = 0; index < length; index++) {
                input.readInt();
            }
            metrics.intArrays = Math.addExact(metrics.intArrays, 1L);
            metrics.intArrayElements = Math.addExact(metrics.intArrayElements, length);
            metrics.valueElements = Math.addExact(metrics.valueElements, length);
        }

        private void readLongArray() throws IOException {
            var length = readArrayLength();
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
            if ((long) length > limits.arrayElements()) {
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

        private void compoundEntry(String name) {
            compoundEntries = Math.addExact(compoundEntries, 1L);
            valueElements = Math.addExact(valueElements, 1L);
            modifiedUtf8Bytes = Math.addExact(
                    modifiedUtf8Bytes,
                    P4E0ResearchNbtMetrics.modifiedUtf8Length(name));
        }

        private void scalar() {
            scalarCount = Math.addExact(scalarCount, 1L);
        }

        private void string(String value) {
            scalar();
            stringCount = Math.addExact(stringCount, 1L);
            modifiedUtf8Bytes = Math.addExact(
                    modifiedUtf8Bytes,
                    P4E0ResearchNbtMetrics.modifiedUtf8Length(value));
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
        private long count;
        private boolean actualEofObserved;

        private BoundedInput(InputStream delegate, long maximum) {
            this.delegate = delegate;
            this.observationLimit = Math.addExact(maximum, 1L);
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
