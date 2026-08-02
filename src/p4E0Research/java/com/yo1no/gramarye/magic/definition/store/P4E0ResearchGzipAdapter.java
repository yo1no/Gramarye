package com.yo1no.gramarye.magic.definition.store;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * Research-only, non-authoritative strict gzip prototype. It reuses the reviewed P4-B channel,
 * header, failure-recorder, and decompressed-bound primitives, but does not establish the future
 * P4-E production parser contract.
 */
public final class P4E0ResearchGzipAdapter {
    private static final int BUFFER_BYTES = 8_192;
    private static final int ROOT_FRAMING_BYTES = 3;

    private P4E0ResearchGzipAdapter() {
    }

    /** Writes one deterministic member, then reads it through the strict research chain. */
    public static Observation writeAndRead(
            Path path,
            CompoundTag root,
            Options options,
            long compressedGuard,
            long decompressedGuard,
            long nbtMaterializationQuota) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(options, "options");
        requireFiniteGuard(compressedGuard, "compressedGuard");
        requireFiniteGuard(decompressedGuard, "decompressedGuard");
        requireFiniteGuard(nbtMaterializationQuota, "nbtMaterializationQuota");

        var written = writeMember(
                path, root, options, compressedGuard, decompressedGuard);
        var observed = readMember(
                path,
                options.maximumNbtDepth(),
                compressedGuard,
                decompressedGuard,
                nbtMaterializationQuota);
        if (written.physicalFileBytes() != observed.physicalFileBytes()
                || written.headerBytes() != observed.gzipHeaderBytes()
                || written.decompressedBytes() != observed.decompressedRootBytes()) {
            throw new IOException("research gzip write/read metrics differ");
        }
        return observed;
    }

    /** Reads one existing synthetic fixture through the strict research chain at depth 512. */
    public static Observation read(
            Path path,
            long compressedGuard,
            long decompressedGuard,
            long nbtMaterializationQuota) throws IOException {
        Objects.requireNonNull(path, "path");
        requireFiniteGuard(compressedGuard, "compressedGuard");
        requireFiniteGuard(decompressedGuard, "decompressedGuard");
        requireFiniteGuard(nbtMaterializationQuota, "nbtMaterializationQuota");
        return readMember(
                path,
                512,
                compressedGuard,
                decompressedGuard,
                nbtMaterializationQuota);
    }

    /**
     * Strictly drains one member without materializing NBT. This research-only observation lets
     * the depth-513 negative fixture retain exact wire metrics after the locked platform decoder
     * rejects its container depth.
     */
    public static WireObservation readWireDrain(
            Path path,
            long compressedGuard,
            long decompressedGuard) throws IOException {
        Objects.requireNonNull(path, "path");
        requireFiniteGuard(compressedGuard, "compressedGuard");
        requireFiniteGuard(decompressedGuard, "decompressedGuard");
        return drainMember(path, compressedGuard, decompressedGuard);
    }

    private static WriteFacts writeMember(
            Path path,
            CompoundTag root,
            Options options,
            long compressedGuard,
            long decompressedGuard) throws IOException {
        var parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("research gzip path has no parent");
        }
        Files.createDirectories(parent);
        if (Files.isSymbolicLink(path)) {
            throw new IOException("research gzip output is a symbolic link");
        }

        var crc = new CRC32();
        long headerBytes;
        long decompressedBytes;
        try (var file = new GuardedCountingOutputStream(
                Files.newOutputStream(
                        path,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE),
                compressedGuard)) {
            var header = new HeaderOutput(file);
            writeHeader(header, options);
            headerBytes = file.byteCount();

            var deflater = new Deflater(options.compressionLevel(), true);
            try {
                var compressed = new DeflaterOutputStream(
                        file, deflater, BUFFER_BYTES, false);
                var rootOutput = new RootAccountingOutputStream(
                        compressed, crc, decompressedGuard);
                var data = new DataOutputStream(rootOutput);
                NbtIo.write(root, data);
                data.flush();
                compressed.finish();
                decompressedBytes = rootOutput.byteCount();
            } finally {
                deflater.end();
            }

            writeLittleEndianInt(file, crc.getValue());
            writeLittleEndianInt(file, decompressedBytes & 0xffff_ffffL);
            file.flush();
            return new WriteFacts(
                    file.byteCount(), headerBytes, decompressedBytes);
        }
    }

    private static Observation readMember(
            Path path,
            int maximumNbtDepth,
            long compressedGuard,
            long decompressedGuard,
            long nbtMaterializationQuota) throws IOException {
        var before = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireRegularIdentity(before);
        var baselineSize = before.size();
        if (baselineSize > compressedGuard) {
            throw new IOException("research compressed guard exceeded");
        }

        try (var channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() != baselineSize) {
                throw new IOException("research gzip size changed before read");
            }
            var recorder = new GzipFailureRecorder();
            var boundedChannel = new BoundedChannelInputStream(
                    channel, compressedGuard, recorder);
            var headerMeter = new HeaderMeter(boundedChannel);
            var verifiedHeader = new GzipHeaderVerifier(headerMeter, recorder);
            var compressed = new BufferedInputStream(verifiedHeader, BUFFER_BYTES);

            final CompoundTag decoded;
            final long decompressedBytes;
            try (var gzip = new GzipCompressorInputStream(compressed, false)) {
                var boundedDecompressed = new BoundedDecompressedInputStream(
                        gzip, decompressedGuard, recorder);
                var nbtInput = new BufferedInputStream(boundedDecompressed, BUFFER_BYTES);
                requireUnnamedCompoundFraming(nbtInput);
                try {
                    decoded = NbtIo.read(
                            new DataInputStream(nbtInput),
                            NbtAccounter.create(nbtMaterializationQuota));
                } catch (RuntimeException exception) {
                    throw new IOException("research NBT materialization rejected");
                }
                if (nbtInput.read() != -1) {
                    throw new IOException("research decompressed trailing data");
                }
                if (boundedDecompressed.capacityExceeded()
                        || !boundedDecompressed.delegateEofObserved()) {
                    throw new IOException("research decompressed guard or EOF failed");
                }
                decompressedBytes = boundedDecompressed.byteCount();

                var tail = readCompressedTail(compressed);
                if (tail != CompressedTail.NONE) {
                    throw new IOException(tail == CompressedTail.SECOND_MEMBER
                            ? "research second gzip member"
                            : "research compressed trailing data");
                }
                if (!headerMeter.verified()
                        || !verifiedHeader.verified()
                        || boundedChannel.syntheticEofObserved()
                        || !boundedChannel.actualEofObserved()
                        || boundedChannel.byteCount() != baselineSize
                        || recorder.channelFailure().isPresent()
                        || recorder.gzipFailure().isPresent()) {
                    throw new IOException("research strict gzip state rejected");
                }
            } catch (IOException exception) {
                recorder.recordGzipIOException(exception);
                throw new IOException("research strict gzip read rejected");
            }

            var after = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!sameIdentity(before, after)
                    || channel.size() != baselineSize) {
                throw new IOException("research gzip identity changed during read");
            }
            return new Observation(
                    decoded,
                    baselineSize,
                    headerMeter.headerBytes(),
                    baselineSize,
                    decompressedBytes,
                    ROOT_FRAMING_BYTES);
        }
    }

    private static WireObservation drainMember(
            Path path,
            long compressedGuard,
            long decompressedGuard) throws IOException {
        var before = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        requireRegularIdentity(before);
        var baselineSize = before.size();
        if (baselineSize > compressedGuard) {
            throw new IOException("research compressed guard exceeded");
        }

        try (var channel = FileChannel.open(
                path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            if (channel.size() != baselineSize) {
                throw new IOException("research gzip size changed before drain");
            }
            var recorder = new GzipFailureRecorder();
            var boundedChannel = new BoundedChannelInputStream(
                    channel, compressedGuard, recorder);
            var headerMeter = new HeaderMeter(boundedChannel);
            var verifiedHeader = new GzipHeaderVerifier(headerMeter, recorder);
            var compressed = new BufferedInputStream(verifiedHeader, BUFFER_BYTES);

            final long decompressedBytes;
            try (var gzip = new GzipCompressorInputStream(compressed, false)) {
                var boundedDecompressed = new BoundedDecompressedInputStream(
                        gzip, decompressedGuard, recorder);
                var payload = new BufferedInputStream(
                        boundedDecompressed, BUFFER_BYTES);
                requireUnnamedCompoundFraming(payload);
                var buffer = new byte[BUFFER_BYTES];
                while (payload.read(buffer) != -1) {
                    // Deliberately discard bounded chunks; no NBT tree is materialized here.
                }
                if (boundedDecompressed.capacityExceeded()
                        || !boundedDecompressed.delegateEofObserved()) {
                    throw new IOException("research decompressed guard or EOF failed");
                }
                decompressedBytes = boundedDecompressed.byteCount();

                var tail = readCompressedTail(compressed);
                if (tail != CompressedTail.NONE) {
                    throw new IOException(tail == CompressedTail.SECOND_MEMBER
                            ? "research second gzip member"
                            : "research compressed trailing data");
                }
                if (!headerMeter.verified()
                        || !verifiedHeader.verified()
                        || boundedChannel.syntheticEofObserved()
                        || !boundedChannel.actualEofObserved()
                        || boundedChannel.byteCount() != baselineSize
                        || recorder.channelFailure().isPresent()
                        || recorder.gzipFailure().isPresent()) {
                    throw new IOException("research strict gzip state rejected");
                }
            } catch (IOException exception) {
                recorder.recordGzipIOException(exception);
                throw new IOException("research strict gzip drain rejected");
            }

            var after = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!sameIdentity(before, after)
                    || channel.size() != baselineSize) {
                throw new IOException("research gzip identity changed during drain");
            }
            return new WireObservation(
                    baselineSize,
                    headerMeter.headerBytes(),
                    baselineSize,
                    decompressedBytes,
                    ROOT_FRAMING_BYTES);
        }
    }

    private static void requireUnnamedCompoundFraming(BufferedInputStream input)
            throws IOException {
        input.mark(ROOT_FRAMING_BYTES);
        if (input.read() != Tag.TAG_COMPOUND || input.read() != 0 || input.read() != 0) {
            throw new IOException("research NBT root framing is not unnamed Compound");
        }
        input.reset();
    }

    private static void writeHeader(HeaderOutput output, Options options) throws IOException {
        var flags = (options.fhcrc() ? 0x02 : 0)
                | (options.extraBytes() > 0 ? 0x04 : 0)
                | (options.fileNameBytes() > 0 ? 0x08 : 0)
                | (options.commentBytes() > 0 ? 0x10 : 0);
        output.write(0x1f);
        output.write(0x8b);
        output.write(8);
        output.write(flags);
        output.write(0);
        output.write(0);
        output.write(0);
        output.write(0);
        output.write(options.compressionLevel() == Deflater.BEST_COMPRESSION ? 2
                : options.compressionLevel() == Deflater.BEST_SPEED ? 4 : 0);
        output.write(255);
        if (options.extraBytes() > 0) {
            output.write(options.extraBytes() & 0xff);
            output.write(options.extraBytes() >>> 8);
            output.writeRepeated(options.optionalFieldByte(), options.extraBytes());
        }
        if (options.fileNameBytes() > 0) {
            output.writeRepeated(options.optionalFieldByte(), options.fileNameBytes());
            output.write(0);
        }
        if (options.commentBytes() > 0) {
            output.writeRepeated(options.optionalFieldByte(), options.commentBytes());
            output.write(0);
        }
        if (options.fhcrc()) {
            var low16 = (int) output.crcValue() & 0xffff;
            output.writeWithoutCrc(low16 & 0xff);
            output.writeWithoutCrc(low16 >>> 8);
        }
    }

    private static void writeLittleEndianInt(OutputStream output, long value)
            throws IOException {
        output.write((int) value & 0xff);
        output.write((int) (value >>> 8) & 0xff);
        output.write((int) (value >>> 16) & 0xff);
        output.write((int) (value >>> 24) & 0xff);
    }

    private static CompressedTail readCompressedTail(BufferedInputStream compressed)
            throws IOException {
        var first = compressed.read();
        if (first == -1) {
            return CompressedTail.NONE;
        }
        if (first == 0x1f && compressed.read() == 0x8b) {
            return CompressedTail.SECOND_MEMBER;
        }
        return CompressedTail.TRAILING_DATA;
    }

    private static void requireRegularIdentity(BasicFileAttributes attributes)
            throws IOException {
        if (!attributes.isRegularFile()
                || attributes.isSymbolicLink()
                || attributes.fileKey() == null) {
            throw new IOException("research gzip file identity is unsupported");
        }
    }

    private static boolean sameIdentity(
            BasicFileAttributes first, BasicFileAttributes second) {
        return second.isRegularFile()
                && !second.isSymbolicLink()
                && first.fileKey().equals(second.fileKey())
                && first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime());
    }

    private static void requireFiniteGuard(long value, String name) {
        if (value <= 0 || value == Long.MAX_VALUE) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    public record Options(
            int extraBytes,
            int fileNameBytes,
            int commentBytes,
            boolean fhcrc,
            int compressionLevel,
            int optionalFieldByte,
            int maximumNbtDepth) {
        public Options {
            if (extraBytes < 0 || extraBytes > 0xffff
                    || fileNameBytes < 0
                    || commentBytes < 0
                    || compressionLevel < Deflater.DEFAULT_COMPRESSION
                    || compressionLevel > Deflater.BEST_COMPRESSION
                    || optionalFieldByte <= 0
                    || optionalFieldByte > 0xff
                    || maximumNbtDepth != 512) {
                throw new IllegalArgumentException("research gzip options are invalid");
            }
        }

        public static Options canonical() {
            return new Options(0, 0, 0, false, Deflater.DEFAULT_COMPRESSION, 0x5a, 512);
        }

        /** Deterministic smoke header containing every supported RFC 1952 optional field. */
        public static Options smokeOptionalFields() {
            return new Options(32, 96, 128, true, Deflater.DEFAULT_COMPRESSION, 0x5a, 512);
        }

        /** Research-only exact physical-size lever; the caller owns the target derivation. */
        public static Options fileNameTarget(int fileNameBytes) {
            return new Options(
                    0,
                    fileNameBytes,
                    0,
                    false,
                    Deflater.DEFAULT_COMPRESSION,
                    0x5a,
                    512);
        }
    }

    public record Observation(
            CompoundTag decodedRoot,
            long physicalFileBytes,
            long gzipHeaderBytes,
            long compressedMemberBytes,
            long decompressedRootBytes,
            long rootFramingBytes) {
        public Observation {
            decodedRoot = Objects.requireNonNull(decodedRoot, "decodedRoot").copy();
            if (physicalFileBytes <= 0
                    || gzipHeaderBytes < 10
                    || compressedMemberBytes <= 0
                    || decompressedRootBytes < ROOT_FRAMING_BYTES
                    || rootFramingBytes != ROOT_FRAMING_BYTES) {
                throw new IllegalArgumentException("research gzip observation is invalid");
            }
        }

        @Override
        public CompoundTag decodedRoot() {
            return decodedRoot.copy();
        }
    }

    /** Strict wire-only metrics for a member whose NBT tree is intentionally not materialized. */
    public record WireObservation(
            long physicalFileBytes,
            long gzipHeaderBytes,
            long compressedMemberBytes,
            long decompressedRootBytes,
            long rootFramingBytes) {
        public WireObservation {
            if (physicalFileBytes <= 0
                    || gzipHeaderBytes < 10
                    || compressedMemberBytes <= 0
                    || decompressedRootBytes < ROOT_FRAMING_BYTES
                    || rootFramingBytes != ROOT_FRAMING_BYTES) {
                throw new IllegalArgumentException(
                        "research gzip wire observation is invalid");
            }
        }
    }

    private record WriteFacts(
            long physicalFileBytes,
            long headerBytes,
            long decompressedBytes) {
    }

    private enum CompressedTail {
        NONE,
        SECOND_MEMBER,
        TRAILING_DATA
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

    private static final class GuardedCountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final long observationLimit;
        private long byteCount;

        private GuardedCountingOutputStream(OutputStream delegate, long maximumBytes) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.observationLimit = Math.addExact(maximumBytes, 1L);
        }

        @Override
        public void write(int value) throws IOException {
            if (byteCount == observationLimit) {
                throw new IOException("research output guard exceeded");
            }
            delegate.write(value);
            byteCount = Math.addExact(byteCount, 1L);
            if (byteCount == observationLimit) {
                throw new IOException("research output guard exceeded");
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            var permitted = (int) Math.min((long) length, observationLimit - byteCount);
            if (permitted > 0) {
                delegate.write(bytes, offset, permitted);
                byteCount = Math.addExact(byteCount, permitted);
            }
            if (permitted != length || byteCount == observationLimit) {
                throw new IOException("research output guard exceeded");
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

        private long byteCount() {
            return byteCount;
        }
    }

    private static final class RootAccountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private final CRC32 crc;
        private final long observationLimit;
        private long byteCount;

        private RootAccountingOutputStream(
                OutputStream delegate, CRC32 crc, long maximumBytes) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.crc = Objects.requireNonNull(crc, "crc");
            this.observationLimit = Math.addExact(maximumBytes, 1L);
        }

        @Override
        public void write(int value) throws IOException {
            if (byteCount == observationLimit) {
                throw new IOException("research decompressed guard exceeded");
            }
            delegate.write(value);
            crc.update(value);
            byteCount = Math.addExact(byteCount, 1L);
            if (byteCount == observationLimit) {
                throw new IOException("research decompressed guard exceeded");
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            var permitted = (int) Math.min((long) length, observationLimit - byteCount);
            if (permitted > 0) {
                delegate.write(bytes, offset, permitted);
                crc.update(bytes, offset, permitted);
                byteCount = Math.addExact(byteCount, permitted);
            }
            if (permitted != length || byteCount == observationLimit) {
                throw new IOException("research decompressed guard exceeded");
            }
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        private long byteCount() {
            return byteCount;
        }
    }

    /** Pass-through mirror used only to expose the exact original RFC1952 header length. */
    private static final class HeaderMeter extends InputStream {
        private final InputStream delegate;
        private final byte[] oneByte = new byte[1];
        private HeaderState state = HeaderState.ID1;
        private int flags;
        private int fixedRemaining;
        private int extraRemaining;
        private long headerBytes;

        private HeaderMeter(InputStream delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public int read() throws IOException {
            return read(oneByte, 0, 1) == -1 ? -1 : oneByte[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            var count = delegate.read(bytes, offset, length);
            if (count <= 0) {
                return count;
            }
            for (var index = offset; index < offset + count && !verified(); index++) {
                accept(bytes[index] & 0xff);
            }
            return count;
        }

        private void accept(int value) {
            headerBytes = Math.addExact(headerBytes, 1L);
            switch (state) {
                case ID1 -> state = HeaderState.ID2;
                case ID2 -> state = HeaderState.METHOD;
                case METHOD -> state = HeaderState.FLAGS;
                case FLAGS -> {
                    flags = value;
                    fixedRemaining = 6;
                    state = HeaderState.FIXED;
                }
                case FIXED -> {
                    fixedRemaining--;
                    if (fixedRemaining == 0) {
                        advanceAfterFixed();
                    }
                }
                case EXTRA_LOW -> {
                    extraRemaining = value;
                    state = HeaderState.EXTRA_HIGH;
                }
                case EXTRA_HIGH -> {
                    extraRemaining |= value << 8;
                    state = extraRemaining == 0 ? afterExtra() : HeaderState.EXTRA;
                }
                case EXTRA -> {
                    extraRemaining--;
                    if (extraRemaining == 0) {
                        state = afterExtra();
                    }
                }
                case NAME -> {
                    if (value == 0) {
                        state = afterName();
                    }
                }
                case COMMENT -> {
                    if (value == 0) {
                        state = afterComment();
                    }
                }
                case FHCRC_LOW -> state = HeaderState.FHCRC_HIGH;
                case FHCRC_HIGH -> state = HeaderState.VERIFIED;
                case VERIFIED -> throw new IllegalStateException("verified header was reparsed");
            }
        }

        private void advanceAfterFixed() {
            state = (flags & 0x04) != 0 ? HeaderState.EXTRA_LOW : afterExtra();
        }

        private HeaderState afterExtra() {
            return (flags & 0x08) != 0 ? HeaderState.NAME : afterName();
        }

        private HeaderState afterName() {
            return (flags & 0x10) != 0 ? HeaderState.COMMENT : afterComment();
        }

        private HeaderState afterComment() {
            return (flags & 0x02) != 0 ? HeaderState.FHCRC_LOW : HeaderState.VERIFIED;
        }

        private boolean verified() {
            return state == HeaderState.VERIFIED;
        }

        private long headerBytes() {
            if (!verified()) {
                throw new IllegalStateException("research gzip header was not complete");
            }
            return headerBytes;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        private enum HeaderState {
            ID1,
            ID2,
            METHOD,
            FLAGS,
            FIXED,
            EXTRA_LOW,
            EXTRA_HIGH,
            EXTRA,
            NAME,
            COMMENT,
            FHCRC_LOW,
            FHCRC_HIGH,
            VERIFIED
        }
    }
}
