package com.yo1no.gramarye.magic.definition.store;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.CRC32;
import net.minecraft.core.HolderLookup;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Strict, bounded, single-member gzip boundary for one already-open primary channel. */
final class StrictSingleMemberGzipInput {
    /** Commons Compress 1.26 reads deflate input in 8192-byte marked windows. */
    private static final int COMMONS_LOOKAHEAD_BYTES = 8_192;

    private StrictSingleMemberGzipInput() {
    }

    /**
     * Loads one gzip member without taking ownership of {@code channel}.
     *
     * <p>{@code baselineSize} is the already-validated pre-read channel size. The caller retains
     * the channel so the filesystem boundary can perform its final identity/size checks and close
     * it. This method never installs or publishes the returned candidate.</p>
     */
    static StrictSingleMemberGzipResult load(
            FileChannel channel,
            long baselineSize,
            long maximumCompressedBytes,
            Optional<HolderLookup.Provider> provider) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(provider, "provider");
        if (baselineSize < 0) {
            throw new IllegalArgumentException("baselineSize must be non-negative");
        }
        if (maximumCompressedBytes <= 0 || maximumCompressedBytes == Long.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "maximumCompressedBytes must allow one finite probe byte");
        }
        if (baselineSize > maximumCompressedBytes) {
            return failure(new SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded(
                    baselineSize, maximumCompressedBytes));
        }

        var recorder = new GzipFailureRecorder();
        var boundedChannel = new BoundedChannelInputStream(
                channel,
                maximumCompressedBytes,
                recorder);
        var verifiedHeader = new GzipHeaderVerifier(boundedChannel, recorder);
        var bufferedCompressed =
                new BufferedInputStream(verifiedHeader, COMMONS_LOOKAHEAD_BYTES);

        try (var gzip = new GzipCompressorInputStream(bufferedCompressed, false)) {
            var boundedDecompressed = new BoundedDecompressedInputStream(
                    gzip,
                    SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES,
                    recorder);
            var carrierResult = SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                    boundedDecompressed, provider);

            var infrastructureFailure = infrastructureFailure(
                    recorder, boundedChannel, baselineSize);
            if (infrastructureFailure.isPresent()) {
                return failure(infrastructureFailure.orElseThrow());
            }

            // B1 intentionally receives max+1 followed by synthetic EOF so a decompression bomb
            // terminates at the approved whole-root bound rather than being drained indefinitely.
            if (boundedDecompressed.capacityExceeded()) {
                if (carrierResult instanceof SkillSavedDataCarrierLoadResult.Failure failed) {
                    return carrierFailure(failed.failure());
                }
                throw new IllegalStateException(
                        "B1 accepted a decompressed stream beyond its approved bound");
            }
            if (carrierResult instanceof SkillSavedDataCarrierLoadResult.Failure failed) {
                // Once B1 has a safe malformed/capacity classification, fail closed without
                // draining attacker-controlled decompressed bytes merely to find another error.
                return carrierFailure(failed.failure());
            }
            if (!boundedDecompressed.delegateEofObserved()) {
                throw new IllegalStateException(
                        "B1 published Ready before observing decompressed member EOF");
            }

            var compressedTail = readCompressedTail(bufferedCompressed);
            infrastructureFailure = infrastructureFailure(
                    recorder, boundedChannel, baselineSize);
            if (infrastructureFailure.isPresent()) {
                return failure(infrastructureFailure.orElseThrow());
            }
            if (compressedTail == CompressedTail.SECOND_MEMBER) {
                return failure(SkillSavedDataPrimaryFailure.MultipleGzipMembers.INSTANCE);
            }
            if (compressedTail == CompressedTail.TRAILING_DATA) {
                return failure(SkillSavedDataPrimaryFailure.CompressedTrailingData.INSTANCE);
            }
            if (!boundedChannel.actualEofObserved()
                    || boundedChannel.byteCount() != baselineSize) {
                throw new IllegalStateException(
                        "compressed EOF did not match the validated channel baseline");
            }

            var ready = (SkillSavedDataCarrierLoadResult.Ready) carrierResult;
            return new StrictSingleMemberGzipResult.Ready(ready.candidate());
        } catch (IOException exception) {
            recorder.recordGzipIOException(exception);
            return failure(infrastructureFailure(recorder, boundedChannel, baselineSize)
                    .orElseThrow());
        }
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

    /** Channel I/O wins over an observed size race, which wins over gzip classification. */
    private static Optional<SkillSavedDataPrimaryFailure> infrastructureFailure(
            GzipFailureRecorder recorder,
            BoundedChannelInputStream channel,
            long baselineSize) {
        var recorded = recorder.channelFailure();
        if (recorded.isPresent()) {
            return recorded;
        }
        if (channel.byteCount() > baselineSize) {
            return Optional.of(new SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected(
                    SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.GREW_DURING_READ));
        }
        if (channel.actualEofObserved() && channel.byteCount() < baselineSize) {
            return Optional.of(new SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected(
                    SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.SHRANK_DURING_READ));
        }
        return recorder.gzipFailure();
    }

    private static StrictSingleMemberGzipResult.Failure carrierFailure(
            SkillSavedDataCarrierFailure failure) {
        return failure(new SkillSavedDataPrimaryFailure.DecompressedCarrierFailure(failure));
    }

    private static StrictSingleMemberGzipResult.Failure failure(
            SkillSavedDataPrimaryFailure failure) {
        return new StrictSingleMemberGzipResult.Failure(failure);
    }

    private enum CompressedTail {
        NONE,
        SECOND_MEMBER,
        TRAILING_DATA
    }
}

/** Total result of strict gzip ingress; no filesystem absence branch exists at this layer. */
sealed interface StrictSingleMemberGzipResult
        permits StrictSingleMemberGzipResult.Ready, StrictSingleMemberGzipResult.Failure {
    record Ready(SkillSavedDataReadyCandidate candidate) implements StrictSingleMemberGzipResult {
        public Ready {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    record Failure(SkillSavedDataPrimaryFailure failure) implements StrictSingleMemberGzipResult {
        public Failure {
            Objects.requireNonNull(failure, "failure");
        }
    }
}

/**
 * Borrowed-channel stream that exposes at most {@code maximumBytes + 1} bytes.
 *
 * <p>Closing this stream does not close the caller-owned channel.</p>
 */
final class BoundedChannelInputStream extends InputStream {
    private final FileChannel channel;
    private final long observationLimit;
    private final GzipFailureRecorder recorder;
    private final byte[] oneByte = new byte[1];
    private long byteCount;
    private boolean actualEofObserved;
    private boolean syntheticEofObserved;
    private boolean closed;

    BoundedChannelInputStream(
            FileChannel channel, long maximumBytes, GzipFailureRecorder recorder) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        if (maximumBytes <= 0 || maximumBytes == Long.MAX_VALUE) {
            throw new IllegalArgumentException("maximumBytes must allow one finite probe byte");
        }
        this.observationLimit = maximumBytes + 1;
    }

    @Override
    public int read() throws IOException {
        return read(oneByte, 0, 1) == -1 ? -1 : oneByte[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        requireOpen();
        if (byteCount == observationLimit) {
            syntheticEofObserved = true;
            return -1;
        }

        var permitted = (int) Math.min((long) length, observationLimit - byteCount);
        final int count;
        try {
            count = channel.read(ByteBuffer.wrap(bytes, offset, permitted));
        } catch (IOException exception) {
            recorder.recordChannelFailure();
            throw exception;
        }
        if (count == -1) {
            actualEofObserved = true;
            return -1;
        }
        if (count == 0) {
            recorder.recordChannelFailure();
            throw new IOException();
        }
        byteCount = Math.addExact(byteCount, count);
        return count;
    }

    long byteCount() {
        return byteCount;
    }

    boolean actualEofObserved() {
        return actualEofObserved;
    }

    boolean syntheticEofObserved() {
        return syntheticEofObserved;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void requireOpen() throws IOException {
        if (closed) {
            recorder.recordChannelFailure();
            throw new IOException();
        }
    }
}

/** Streaming pass-through verifier for the original RFC 1952 header, including FHCRC. */
final class GzipHeaderVerifier extends InputStream {
    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;
    private static final int RESERVED_FLAGS = 0xe0;

    private final InputStream delegate;
    private final GzipFailureRecorder recorder;
    private final CRC32 headerCrc = new CRC32();
    private final byte[] oneByte = new byte[1];
    private HeaderState state = HeaderState.ID1;
    private int flags;
    private int fixedBytesRemaining;
    private int extraBytesRemaining;
    private int expectedHeaderCrc;

    GzipHeaderVerifier(InputStream delegate, GzipFailureRecorder recorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    @Override
    public int read() throws IOException {
        return read(oneByte, 0, 1) == -1 ? -1 : oneByte[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        var count = delegate.read(bytes, offset, length);
        if (count == -1) {
            if (state != HeaderState.VERIFIED) {
                reject(SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
            }
            return -1;
        }
        for (var index = offset; index < offset + count && state != HeaderState.VERIFIED; index++) {
            accept(bytes[index] & 0xff);
        }
        return count;
    }

    boolean verified() {
        return state == HeaderState.VERIFIED;
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    private void accept(int value) throws IOException {
        if (state != HeaderState.FHCRC_LOW && state != HeaderState.FHCRC_HIGH) {
            headerCrc.update(value);
        }
        switch (state) {
            case ID1 -> {
                require(value == 0x1f, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
                state = HeaderState.ID2;
            }
            case ID2 -> {
                require(value == 0x8b, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
                state = HeaderState.COMPRESSION_METHOD;
            }
            case COMPRESSION_METHOD -> {
                require(value == 8, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
                state = HeaderState.FLAGS;
            }
            case FLAGS -> {
                require((value & RESERVED_FLAGS) == 0,
                        SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
                flags = value;
                fixedBytesRemaining = 6;
                state = HeaderState.FIXED_FIELDS;
            }
            case FIXED_FIELDS -> {
                fixedBytesRemaining--;
                if (fixedBytesRemaining == 0) {
                    advanceAfterFixedFields();
                }
            }
            case EXTRA_LENGTH_LOW -> {
                extraBytesRemaining = value;
                state = HeaderState.EXTRA_LENGTH_HIGH;
            }
            case EXTRA_LENGTH_HIGH -> {
                extraBytesRemaining |= value << 8;
                if (extraBytesRemaining == 0) {
                    advanceAfterExtra();
                } else {
                    state = HeaderState.EXTRA;
                }
            }
            case EXTRA -> {
                extraBytesRemaining--;
                if (extraBytesRemaining == 0) {
                    advanceAfterExtra();
                }
            }
            case NAME -> {
                if (value == 0) {
                    advanceAfterName();
                }
            }
            case COMMENT -> {
                if (value == 0) {
                    advanceAfterComment();
                }
            }
            case FHCRC_LOW -> {
                expectedHeaderCrc = value;
                state = HeaderState.FHCRC_HIGH;
            }
            case FHCRC_HIGH -> {
                expectedHeaderCrc |= value << 8;
                require(expectedHeaderCrc == ((int) headerCrc.getValue() & 0xffff),
                        SkillSavedDataPrimaryFailure.GzipFailureKind.FHCRC_INVALID);
                state = HeaderState.VERIFIED;
            }
            case VERIFIED -> throw new IllegalStateException("verified header was reparsed");
        }
    }

    private void advanceAfterFixedFields() {
        if ((flags & FEXTRA) != 0) {
            state = HeaderState.EXTRA_LENGTH_LOW;
        } else {
            advanceAfterExtra();
        }
    }

    private void advanceAfterExtra() {
        if ((flags & FNAME) != 0) {
            state = HeaderState.NAME;
        } else {
            advanceAfterName();
        }
    }

    private void advanceAfterName() {
        if ((flags & FCOMMENT) != 0) {
            state = HeaderState.COMMENT;
        } else {
            advanceAfterComment();
        }
    }

    private void advanceAfterComment() {
        state = (flags & FHCRC) != 0 ? HeaderState.FHCRC_LOW : HeaderState.VERIFIED;
    }

    private void require(boolean condition, SkillSavedDataPrimaryFailure.GzipFailureKind failure)
            throws IOException {
        if (!condition) {
            reject(failure);
        }
    }

    private void reject(SkillSavedDataPrimaryFailure.GzipFailureKind failure) throws IOException {
        recorder.recordGzipFailure(failure);
        throw new IOException();
    }

    private enum HeaderState {
        ID1,
        ID2,
        COMPRESSION_METHOD,
        FLAGS,
        FIXED_FIELDS,
        EXTRA_LENGTH_LOW,
        EXTRA_LENGTH_HIGH,
        EXTRA,
        NAME,
        COMMENT,
        FHCRC_LOW,
        FHCRC_HIGH,
        VERIFIED
    }
}

/** Per-load typed failure recorder; it never retains an exception or its message. */
final class GzipFailureRecorder {
    private State state = State.NONE;

    void recordChannelFailure() {
        state = State.CHANNEL_IO;
    }

    void recordGzipFailure(SkillSavedDataPrimaryFailure.GzipFailureKind failure) {
        Objects.requireNonNull(failure, "failure");
        if (state == State.NONE) {
            state = switch (failure) {
                case HEADER_INVALID -> State.HEADER_INVALID;
                case FHCRC_INVALID -> State.FHCRC_INVALID;
                case DEFLATE_INVALID -> State.DEFLATE_INVALID;
                case TRAILER_CRC_INVALID -> State.TRAILER_CRC_INVALID;
                case TRAILER_ISIZE_INVALID -> State.TRAILER_ISIZE_INVALID;
                case TRUNCATED -> State.TRUNCATED;
            };
        }
    }

    void recordGzipIOException(IOException exception) {
        Objects.requireNonNull(exception, "exception");
        recordGzipFailure(exception instanceof EOFException
                ? SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED
                : SkillSavedDataPrimaryFailure.GzipFailureKind.DEFLATE_INVALID);
    }

    Optional<SkillSavedDataPrimaryFailure> channelFailure() {
        return state == State.CHANNEL_IO
                ? Optional.of(new SkillSavedDataPrimaryFailure.OuterSavedDataUnreadable(
                        SkillSavedDataPrimaryFailure.PrimaryIngressStage.READ_CHANNEL))
                : Optional.empty();
    }

    Optional<SkillSavedDataPrimaryFailure> gzipFailure() {
        return switch (state) {
            case NONE, CHANNEL_IO -> Optional.empty();
            case HEADER_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
            case FHCRC_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.FHCRC_INVALID);
            case DEFLATE_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.DEFLATE_INVALID);
            case TRAILER_CRC_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.TRAILER_CRC_INVALID);
            case TRAILER_ISIZE_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.TRAILER_ISIZE_INVALID);
            case TRUNCATED -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        };
    }

    private static Optional<SkillSavedDataPrimaryFailure> malformed(
            SkillSavedDataPrimaryFailure.GzipFailureKind kind) {
        return Optional.of(new SkillSavedDataPrimaryFailure.MalformedGzip(kind));
    }

    enum State {
        NONE,
        CHANNEL_IO,
        HEADER_INVALID,
        FHCRC_INVALID,
        DEFLATE_INVALID,
        TRAILER_CRC_INVALID,
        TRAILER_ISIZE_INVALID,
        TRUNCATED
    }
}

/** Bounded decompressed stream that records gzip failures before B1 converts them. */
final class BoundedDecompressedInputStream extends InputStream {
    private final InputStream delegate;
    private final long maximumBytes;
    private final long observationLimit;
    private final GzipFailureRecorder recorder;
    private final byte[] oneByte = new byte[1];
    private long byteCount;
    private boolean delegateEofObserved;
    private boolean syntheticEofObserved;

    BoundedDecompressedInputStream(
            InputStream delegate, long maximumBytes, GzipFailureRecorder recorder) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        if (maximumBytes <= 0 || maximumBytes == Long.MAX_VALUE) {
            throw new IllegalArgumentException("maximumBytes must allow one finite probe byte");
        }
        this.maximumBytes = maximumBytes;
        this.observationLimit = maximumBytes + 1;
    }

    @Override
    public int read() throws IOException {
        return read(oneByte, 0, 1) == -1 ? -1 : oneByte[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        if (length == 0) {
            return 0;
        }
        if (byteCount == observationLimit) {
            syntheticEofObserved = true;
            return -1;
        }

        var permitted = (int) Math.min((long) length, observationLimit - byteCount);
        final int count;
        try {
            count = delegate.read(bytes, offset, permitted);
        } catch (IOException exception) {
            recorder.recordGzipIOException(exception);
            throw exception;
        }
        if (count == -1) {
            delegateEofObserved = true;
            return -1;
        }
        byteCount = Math.addExact(byteCount, count);
        return count;
    }

    long byteCount() {
        return byteCount;
    }

    boolean capacityExceeded() {
        return byteCount > maximumBytes;
    }

    boolean delegateEofObserved() {
        return delegateEofObserved;
    }

    boolean syntheticEofObserved() {
        return syntheticEofObserved;
    }
}
