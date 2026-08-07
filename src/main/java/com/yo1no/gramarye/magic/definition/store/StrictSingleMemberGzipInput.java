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
                new StrictSingleMemberGzipCore.TrailerTrackingBufferedInputStream(
                        verifiedHeader,
                        StrictSingleMemberGzipCore.MINIMUM_BUFFER_BYTES);

        try {
            return StrictSingleMemberGzipCore.read(
                    bufferedCompressed,
                    SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES,
                    recorder,
                    StrictSingleMemberGzipCore.ByteCheckpoint.NONE,
                    decompressed -> SkillSavedDataCarrierPersistenceBridge.loadDecompressed(
                            decompressed, provider),
                    (carrierResult, boundedDecompressed, completion) -> {
                        var infrastructureFailure = infrastructureFailure(
                                recorder, boundedChannel, baselineSize);
                        if (infrastructureFailure.isPresent()) {
                            return failure(infrastructureFailure.orElseThrow());
                        }

                        // B1 intentionally receives max+1 followed by synthetic EOF so a
                        // decompression bomb terminates at the approved whole-root bound rather
                        // than being drained indefinitely.
                        if (boundedDecompressed.capacityExceeded()) {
                            if (carrierResult
                                    instanceof SkillSavedDataCarrierLoadResult.Failure failed) {
                                return carrierFailure(failed.failure());
                            }
                            throw new IllegalStateException(
                                    "B1 accepted a decompressed stream beyond its approved bound");
                        }
                        if (carrierResult
                                instanceof SkillSavedDataCarrierLoadResult.Failure failed) {
                            // Once B1 has a safe malformed/capacity classification, fail closed
                            // without draining attacker-controlled decompressed bytes merely to
                            // find another error.
                            return carrierFailure(failed.failure());
                        }
                        if (!boundedDecompressed.delegateEofObserved()) {
                            throw new IllegalStateException(
                                    "B1 published Ready before observing decompressed member EOF");
                        }

                        var compressedTail = completion.verifyMemberCompletion();
                        infrastructureFailure = infrastructureFailure(
                                recorder, boundedChannel, baselineSize);
                        if (infrastructureFailure.isPresent()) {
                            return failure(infrastructureFailure.orElseThrow());
                        }
                        if (compressedTail
                                == StrictSingleMemberGzipCore.CompressedTail.SECOND_MEMBER) {
                            return failure(
                                    SkillSavedDataPrimaryFailure.MultipleGzipMembers.INSTANCE);
                        }
                        if (compressedTail
                                == StrictSingleMemberGzipCore.CompressedTail.TRAILING_DATA) {
                            return failure(
                                    SkillSavedDataPrimaryFailure.CompressedTrailingData.INSTANCE);
                        }
                        if (!boundedChannel.actualEofObserved()
                                || boundedChannel.byteCount() != baselineSize) {
                            throw new IllegalStateException(
                                    "compressed EOF did not match the validated channel baseline");
                        }

                        var ready = (SkillSavedDataCarrierLoadResult.Ready) carrierResult;
                        return new StrictSingleMemberGzipResult.Ready(ready.candidate());
                    });
        } catch (IOException exception) {
            recorder.recordGzipIOException(exception);
            return failure(infrastructureFailure(recorder, boundedChannel, baselineSize)
                    .orElseThrow());
        }
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
        return recorder.savedDataGzipFailure();
    }

    private static StrictSingleMemberGzipResult.Failure carrierFailure(
            SkillSavedDataCarrierFailure failure) {
        return failure(new SkillSavedDataPrimaryFailure.DecompressedCarrierFailure(failure));
    }

    private static StrictSingleMemberGzipResult.Failure failure(
            SkillSavedDataPrimaryFailure failure) {
        return new StrictSingleMemberGzipResult.Failure(failure);
    }

}

/**
 * Reviewed generic single-member gzip stream core shared by bounded source-admission callers.
 *
 * <p>The caller owns the mark/reset-capable compressed buffer and supplies the parser, byte
 * checkpoint, bounded recorder, and completion policy. This class remains the only production
 * owner of the Commons single-member constructor and compressed-tail grammar.</p>
 */
final class StrictSingleMemberGzipCore {
    /** Commons Compress 1.26 reads deflate input in 8192-byte marked windows. */
    static final int MINIMUM_BUFFER_BYTES = 8_192;

    private StrictSingleMemberGzipCore() {
    }

    static <P, R> R read(
            BufferedInputStream bufferedCompressed,
            long maximumDecompressedBytes,
            GzipFailureRecorder recorder,
            ByteCheckpoint decompressedCheckpoint,
            PayloadReader<P> parser,
            CompletionVerifier<P, R> completionVerifier) throws IOException {
        Objects.requireNonNull(bufferedCompressed, "bufferedCompressed");
        Objects.requireNonNull(recorder, "recorder");
        Objects.requireNonNull(decompressedCheckpoint, "decompressedCheckpoint");
        Objects.requireNonNull(parser, "parser");
        Objects.requireNonNull(completionVerifier, "completionVerifier");

        try {
            try (var gzip = new GzipCompressorInputStream(bufferedCompressed, false)) {
                GzipIOExceptionObserver gzipIOExceptionObserver = exception -> {
                    recorder.recordGzipIOException(exception);
                    if (bufferedCompressed
                            instanceof TrailerTrackingBufferedInputStream tracked) {
                        recorder.refineLockedCommonsTrailerFailure(
                                tracked.classifyFailure(exception));
                    }
                };
                var boundedDecompressed = new BoundedDecompressedInputStream(
                        gzip,
                        maximumDecompressedBytes,
                        recorder,
                        decompressedCheckpoint,
                        gzipIOExceptionObserver);
                var parsed = parser.read(boundedDecompressed);
                var completion = new CompressedCompletion(
                        bufferedCompressed, boundedDecompressed);
                return completionVerifier.complete(parsed, boundedDecompressed, completion);
            }
        } catch (IOException exception) {
            if (bufferedCompressed instanceof TrailerTrackingBufferedInputStream tracked) {
                recorder.refineLockedCommonsTrailerFailure(
                        tracked.classifyFailure(exception));
            }
            throw exception;
        }
    }

    @FunctionalInterface
    interface ByteCheckpoint {
        ByteCheckpoint NONE = ignored -> {
        };

        /**
         * Observes one fixed-buffer read before those bytes are returned to the next layer.
         * A rejecting caller records its own bounded classification before throwing.
         */
        void observe(long byteDelta) throws IOException;
    }

    @FunctionalInterface
    interface PayloadReader<P> {
        P read(InputStream decompressed) throws IOException;
    }

    @FunctionalInterface
    interface GzipIOExceptionObserver {
        void observe(IOException exception);
    }

    @FunctionalInterface
    interface CompletionVerifier<P, R> {
        R complete(
                P parsed,
                BoundedDecompressedInputStream decompressed,
                CompressedCompletion completion) throws IOException;
    }

    static final class CompressedCompletion {
        private final BufferedInputStream compressed;
        private final BoundedDecompressedInputStream decompressed;
        private boolean verified;

        private CompressedCompletion(
                BufferedInputStream compressed,
                BoundedDecompressedInputStream decompressed) {
            this.compressed = Objects.requireNonNull(compressed, "compressed");
            this.decompressed = Objects.requireNonNull(decompressed, "decompressed");
        }

        /** Requires decompressed EOF, then classifies the exact tail on the same buffer. */
        CompressedTail verifyMemberCompletion() throws IOException {
            if (verified) {
                throw new IllegalStateException(
                        "gzip member completion was verified more than once");
            }
            if (decompressed.capacityExceeded()) {
                throw new IllegalStateException(
                        "gzip member completion followed decompressed capacity overflow");
            }
            if (!decompressed.delegateEofObserved()) {
                throw new IllegalStateException(
                        "gzip member completion preceded decompressed EOF");
            }
            verified = true;
            var first = compressed.read();
            if (first == -1) {
                return CompressedTail.NONE;
            }
            if (first == 0x1f && compressed.read() == 0x8b) {
                return CompressedTail.SECOND_MEMBER;
            }
            return CompressedTail.TRAILING_DATA;
        }
    }

    enum CompressedTail {
        NONE,
        SECOND_MEMBER,
        TRAILING_DATA
    }

    /**
     * Tracks the locked Commons Compress 1.26 trailer transition without inspecting a stack or
     * retaining an exception. Commons marks before each inflater fill, resets exactly once after
     * {@code Inflater.finished()}, rewinds that fill with one bulk read, and then reads the eight
     * trailer bytes one at a time through {@code DataInput.readUnsignedByte()}.
     */
    static final class TrailerTrackingBufferedInputStream extends BufferedInputStream {
        private TrailerStage trailerStage = TrailerStage.DEFLATE;
        private int trailerBytesRead;

        TrailerTrackingBufferedInputStream(
                InputStream delegate, int bufferSize) {
            super(Objects.requireNonNull(delegate, "delegate"), requireMinimumBuffer(bufferSize));
        }

        @Override
        public synchronized void reset() throws IOException {
            super.reset();
            trailerStage = TrailerStage.REWIND;
            trailerBytesRead = 0;
        }

        @Override
        public synchronized int read() throws IOException {
            var value = super.read();
            if (trailerStage == TrailerStage.REWIND) {
                // No bulk rewind means the inflater consumed the entire marked fill.
                trailerStage = TrailerStage.TRAILER;
            }
            if (trailerStage == TrailerStage.TRAILER && value != -1) {
                trailerBytesRead++;
            }
            return value;
        }

        @Override
        public synchronized int read(byte[] bytes, int offset, int length) throws IOException {
            var count = super.read(bytes, offset, length);
            if (trailerStage == TrailerStage.REWIND
                    && length > 0
                    && count == length) {
                // Commons Compress 1.26 rewinds the inflater fill with one exact bulk read.
                trailerStage = TrailerStage.TRAILER;
            }
            return count;
        }

        private synchronized TrailerFailure classifyFailure(IOException exception) {
            Objects.requireNonNull(exception, "exception");
            if (trailerStage == TrailerStage.DEFLATE) {
                return TrailerFailure.NONE;
            }
            if (exception instanceof EOFException && trailerBytesRead < 8) {
                return TrailerFailure.TRUNCATED;
            }
            // The reset is the structural trailer boundary. Locked Commons Compress 1.26 then
            // emits these two exact comparison failures; inspect the message transiently only to
            // distinguish them and retain solely the bounded enum below.
            if ("Gzip-compressed data is corrupt (CRC32 error)"
                    .equals(exception.getMessage())) {
                return TrailerFailure.CRC;
            }
            if ("Gzip-compressed data is corrupt(uncompressed size mismatch)"
                    .equals(exception.getMessage())) {
                return TrailerFailure.ISIZE;
            }
            if (trailerBytesRead == 4) {
                return TrailerFailure.CRC;
            }
            if (trailerBytesRead == 8) {
                return TrailerFailure.ISIZE;
            }
            return TrailerFailure.NONE;
        }

        private static int requireMinimumBuffer(int bufferSize) {
            if (bufferSize < MINIMUM_BUFFER_BYTES) {
                throw new IllegalArgumentException(
                        "compressed buffer must preserve Commons lookahead");
            }
            return bufferSize;
        }

        private enum TrailerStage {
            DEFLATE,
            REWIND,
            TRAILER
        }
    }

    enum TrailerFailure {
        NONE,
        CRC,
        ISIZE,
        TRUNCATED
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
    private final StrictSingleMemberGzipCore.ByteCheckpoint checkpoint;
    private final byte[] oneByte = new byte[1];
    private long byteCount;
    private boolean actualEofObserved;
    private boolean syntheticEofObserved;
    private boolean closed;

    BoundedChannelInputStream(
            FileChannel channel, long maximumBytes, GzipFailureRecorder recorder) {
        this(
                channel,
                maximumBytes,
                recorder,
                StrictSingleMemberGzipCore.ByteCheckpoint.NONE);
    }

    BoundedChannelInputStream(
            FileChannel channel,
            long maximumBytes,
            GzipFailureRecorder recorder,
            StrictSingleMemberGzipCore.ByteCheckpoint checkpoint) {
        this.channel = Objects.requireNonNull(channel, "channel");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
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
        checkpoint.observe(count);
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

    /** True only for validation unavailable to the platform's permissive playerdata reader. */
    boolean strictOnlyFailure() {
        return switch (state) {
            case FHCRC_INVALID,
                    TRAILER_CRC_INVALID,
                    TRAILER_ISIZE_INVALID,
                    COMMONS_TRAILER_TRUNCATED -> true;
            case NONE, CHANNEL_IO, HEADER_INVALID, DEFLATE_INVALID, TRUNCATED -> false;
        };
    }

    void refineLockedCommonsTrailerFailure(
            StrictSingleMemberGzipCore.TrailerFailure failure) {
        Objects.requireNonNull(failure, "failure");
        if (state == State.CHANNEL_IO || failure == StrictSingleMemberGzipCore.TrailerFailure.NONE) {
            return;
        }
        state = switch (failure) {
            case NONE -> throw new IllegalStateException("unreachable trailer refinement");
            case CRC -> State.TRAILER_CRC_INVALID;
            case ISIZE -> State.TRAILER_ISIZE_INVALID;
            case TRUNCATED -> State.COMMONS_TRAILER_TRUNCATED;
        };
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
            case TRUNCATED, COMMONS_TRAILER_TRUNCATED -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        };
    }

    /** Preserves the established P4-B classification at its external primary-ingress boundary. */
    Optional<SkillSavedDataPrimaryFailure> savedDataGzipFailure() {
        return switch (state) {
            // Locked Commons 1.26 formerly reached the generic IOException branch for both
            // trailer comparisons. E1 needs their strict-only identity, while P4-B continues to
            // publish its reviewed DEFLATE_INVALID vocabulary.
            case TRAILER_CRC_INVALID, TRAILER_ISIZE_INVALID -> malformed(
                    SkillSavedDataPrimaryFailure.GzipFailureKind.DEFLATE_INVALID);
            case NONE,
                    CHANNEL_IO,
                    HEADER_INVALID,
                    FHCRC_INVALID,
                    DEFLATE_INVALID,
                    TRUNCATED,
                    COMMONS_TRAILER_TRUNCATED -> gzipFailure();
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
        TRUNCATED,
        COMMONS_TRAILER_TRUNCATED
    }
}

/** Bounded decompressed stream that records gzip failures before B1 converts them. */
final class BoundedDecompressedInputStream extends InputStream {
    private final InputStream delegate;
    private final long maximumBytes;
    private final long observationLimit;
    private final StrictSingleMemberGzipCore.ByteCheckpoint checkpoint;
    private final StrictSingleMemberGzipCore.GzipIOExceptionObserver gzipIOExceptionObserver;
    private final byte[] oneByte = new byte[1];
    private long byteCount;
    private boolean delegateEofObserved;
    private boolean syntheticEofObserved;

    BoundedDecompressedInputStream(
            InputStream delegate, long maximumBytes, GzipFailureRecorder recorder) {
        this(
                delegate,
                maximumBytes,
                recorder,
                StrictSingleMemberGzipCore.ByteCheckpoint.NONE,
                recorder::recordGzipIOException);
    }

    BoundedDecompressedInputStream(
            InputStream delegate,
            long maximumBytes,
            GzipFailureRecorder recorder,
            StrictSingleMemberGzipCore.ByteCheckpoint checkpoint) {
        this(
                delegate,
                maximumBytes,
                recorder,
                checkpoint,
                recorder::recordGzipIOException);
    }

    BoundedDecompressedInputStream(
            InputStream delegate,
            long maximumBytes,
            GzipFailureRecorder recorder,
            StrictSingleMemberGzipCore.ByteCheckpoint checkpoint,
            StrictSingleMemberGzipCore.GzipIOExceptionObserver gzipIOExceptionObserver) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(recorder, "recorder");
        this.checkpoint = Objects.requireNonNull(checkpoint, "checkpoint");
        this.gzipIOExceptionObserver = Objects.requireNonNull(
                gzipIOExceptionObserver, "gzipIOExceptionObserver");
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
            gzipIOExceptionObserver.observe(exception);
            throw exception;
        }
        if (count == -1) {
            delegateEofObserved = true;
            return -1;
        }
        checkpoint.observe(count);
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
