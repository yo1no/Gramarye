package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.util.Objects;

/** Same-channel strict gzip plus streaming player-NBT source reader. */
final class P4E1PlayerDataFileReader {
    private P4E1PlayerDataFileReader() {
    }

    static P4E1PlayerDataSourceSelector.SourceReader<
            P4E1PlayerDataNbtScanner.ScanResult.Ready> reader(
            long maximumAttachmentEncodedBytes) {
        if (maximumAttachmentEncodedBytes < 1L
                || maximumAttachmentEncodedBytes == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Attachment byte maximum must be finite and positive");
        }
        return (source, stableSize, scope) -> read(
                source, stableSize, scope, maximumAttachmentEncodedBytes);
    }

    private static P4E1PlayerDataSourceSelector.SourceReadResult<
            P4E1PlayerDataNbtScanner.ScanResult.Ready> read(
            P4E1PlayerDataSourceSelector.SourceInput source,
            long stableSize,
            P4E1AuditBudget.FileScope scope,
            long maximumAttachmentEncodedBytes) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scope, "scope");
        var recorder = new GzipFailureRecorder();
        var perFileMaximum = scope.maximum(P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE);
        var boundedChannel = new BoundedChannelInputStream(
                source.channel(),
                perFileMaximum,
                recorder,
                delta -> checkpointCompressed(scope, delta));
        var verifiedHeader = new GzipHeaderVerifier(boundedChannel, recorder);
        var buffered = new StrictSingleMemberGzipCore.TrailerTrackingBufferedInputStream(
                verifiedHeader,
                StrictSingleMemberGzipCore.MINIMUM_BUFFER_BYTES);
        try {
            return StrictSingleMemberGzipCore.read(
                    buffered,
                    scope.maximum(P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE),
                    recorder,
                    StrictSingleMemberGzipCore.ByteCheckpoint.NONE,
                    decompressed -> P4E1PlayerDataNbtScanner.scan(
                            decompressed, scope, maximumAttachmentEncodedBytes),
                    (scanned, ignored, completion) -> complete(
                            scanned,
                            completion,
                            recorder,
                            boundedChannel,
                            stableSize));
        } catch (P4E1CompressedCapacityRejected rejected) {
            return failure(
                    P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION,
                    P4E1SourceFailure.capacity(rejected.exceeded()));
        } catch (IOException exception) {
            var infrastructure = recorderFailure(recorder);
            if (infrastructure != null) {
                return infrastructure;
            }
            return failure(
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .PLATFORM_READ_FAILURE_PROVEN,
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN,
                            P4E1AuditStage.GZIP_FRAMING));
        }
    }

    private static P4E1PlayerDataSourceSelector.SourceReadResult<
            P4E1PlayerDataNbtScanner.ScanResult.Ready> complete(
            P4E1PlayerDataNbtScanner.ScanResult scanned,
            StrictSingleMemberGzipCore.CompressedCompletion completion,
            GzipFailureRecorder recorder,
            BoundedChannelInputStream channel,
            long stableSize) throws IOException {
        var infrastructure = recorderFailure(recorder);
        if (infrastructure != null) {
            return infrastructure;
        }

        if (scanned instanceof P4E1PlayerDataNbtScanner.ScanResult.Failure failed
                && failed.category()
                        != P4E1PlayerDataSourceSelector.FailureCategory
                                .POST_NBT_SEMANTIC_FAILURE) {
            return failure(failed.category(), failed.failure());
        }

        var tail = completion.verifyMemberCompletion();
        infrastructure = recorderFailure(recorder);
        if (infrastructure != null) {
            return infrastructure;
        }
        if (tail != StrictSingleMemberGzipCore.CompressedTail.NONE) {
            return failure(
                    P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION,
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.STRICT_GZIP_REJECTED,
                            P4E1AuditStage.GZIP_FRAMING));
        }
        if (!channel.actualEofObserved() || channel.byteCount() != stableSize) {
            return failure(
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .FILESYSTEM_OR_RACE_FAILURE,
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.PRIMARY_FILE_RACE_DETECTED,
                            P4E1AuditStage.GZIP_FRAMING));
        }
        if (scanned instanceof P4E1PlayerDataNbtScanner.ScanResult.Failure failed) {
            return failure(failed.category(), failed.failure());
        }
        return new P4E1PlayerDataSourceSelector.SourceReadResult.Ready<>(
                (P4E1PlayerDataNbtScanner.ScanResult.Ready) scanned);
    }

    private static P4E1PlayerDataSourceSelector.SourceReadResult<
            P4E1PlayerDataNbtScanner.ScanResult.Ready> recorderFailure(
            GzipFailureRecorder recorder) {
        if (recorder.channelFailure().isPresent()) {
            return failure(
                    P4E1PlayerDataSourceSelector.FailureCategory
                            .FILESYSTEM_OR_RACE_FAILURE,
                    P4E1SourceFailure.simple(
                            P4E1SourceFailure.Code.PRIMARY_FILE_UNREADABLE,
                            P4E1AuditStage.GZIP_FRAMING));
        }
        var gzip = recorder.gzipFailure();
        if (gzip.isEmpty()) {
            return null;
        }
        var kind = ((SkillSavedDataPrimaryFailure.MalformedGzip) gzip.orElseThrow()).kind();
        var category = recorder.strictOnlyFailure()
                ? P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION
                : switch (kind) {
                    case HEADER_INVALID, DEFLATE_INVALID, TRUNCATED ->
                            P4E1PlayerDataSourceSelector.FailureCategory
                                    .PLATFORM_READ_FAILURE_PROVEN;
                    case FHCRC_INVALID, TRAILER_CRC_INVALID, TRAILER_ISIZE_INVALID ->
                            P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION;
                };
        var code = category
                == P4E1PlayerDataSourceSelector.FailureCategory
                        .PLATFORM_READ_FAILURE_PROVEN
                ? P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN
                : P4E1SourceFailure.Code.STRICT_GZIP_REJECTED;
        return failure(category, P4E1SourceFailure.simple(code, P4E1AuditStage.GZIP_FRAMING));
    }

    private static void checkpointCompressed(
            P4E1AuditBudget.FileScope scope, long delta)
            throws P4E1CompressedCapacityRejected {
        var exceeded = scope.checkpointFileAndAggregate(
                P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                P4E1AuditStage.PER_FILE_COMPRESSED,
                P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                delta);
        if (exceeded.isPresent()) {
            throw new P4E1CompressedCapacityRejected(exceeded.orElseThrow());
        }
    }

    private static P4E1PlayerDataSourceSelector.SourceReadResult.Failure<
            P4E1PlayerDataNbtScanner.ScanResult.Ready> failure(
            P4E1PlayerDataSourceSelector.FailureCategory category,
            P4E1SourceFailure failure) {
        return new P4E1PlayerDataSourceSelector.SourceReadResult.Failure<>(category, failure);
    }

}
