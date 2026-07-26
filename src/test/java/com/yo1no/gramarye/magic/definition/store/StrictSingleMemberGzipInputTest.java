package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Optional;
import java.util.zip.CRC32;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StrictSingleMemberGzipInputTest {
    private static final int FHCRC = 0x02;
    private static final int FEXTRA = 0x04;
    private static final int FNAME = 0x08;
    private static final int FCOMMENT = 0x10;

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalMemberRequiresExactCompressedEofEvenWhenBufferPrefetchesTail()
            throws Exception {
        var canonical = canonicalGzip();

        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(canonical));
        assertFailure(concat(canonical, canonical),
                SkillSavedDataPrimaryFailure.MultipleGzipMembers.class);
        assertFailure(concat(canonical, new byte[] {12, 34}),
                SkillSavedDataPrimaryFailure.CompressedTrailingData.class);
        assertFailure(concat(canonical, new byte[] {1}),
                SkillSavedDataPrimaryFailure.CompressedTrailingData.class);
        assertFailure(concat(canonical, new byte[] {0}),
                SkillSavedDataPrimaryFailure.CompressedTrailingData.class);
        assertFailure(concat(canonical, new byte[] {0x1f}),
                SkillSavedDataPrimaryFailure.CompressedTrailingData.class);
    }

    @Test
    void malformedMagicMethodReservedFlagsAndTruncatedHeaderAreRejected()
            throws Exception {
        var canonical = canonicalGzip();
        var magic = canonical.clone();
        magic[0] = 0;
        var method = canonical.clone();
        method[2] = 7;
        var flags = canonical.clone();
        flags[3] = (byte) 0x20;

        assertMalformed(magic, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
        assertMalformed(method, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
        assertMalformed(flags, SkillSavedDataPrimaryFailure.GzipFailureKind.HEADER_INVALID);
        assertMalformed(
                Arrays.copyOf(canonical, 1),
                SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        assertMalformed(
                Arrays.copyOf(canonical, 9),
                SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
    }

    @Test
    void truncatedBodyTrailerCrcAndIsizeMutationsAreMalformedGzip() throws Exception {
        var canonical = canonicalGzip();
        var body = Arrays.copyOf(canonical, Math.max(11, canonical.length / 2));
        var trailer = Arrays.copyOf(canonical, canonical.length - 1);
        var crc = canonical.clone();
        crc[crc.length - 8] ^= 0x01;
        var isize = canonical.clone();
        isize[isize.length - 4] ^= 0x01;

        assertMalformed(body, SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        assertMalformed(trailer, SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        assertMalformed(crc, SkillSavedDataPrimaryFailure.GzipFailureKind.DEFLATE_INVALID);
        assertMalformed(isize, SkillSavedDataPrimaryFailure.GzipFailureKind.DEFLATE_INVALID);
    }

    @Test
    void fhcrcIsVerifiedStreamingAndOptionalFieldsRemainTransparent() throws Exception {
        var valid = customHeaderGzip(
                FHCRC | FEXTRA | FNAME | FCOMMENT,
                new byte[] {1, 2, 3, 4},
                new byte[] {'n', 'a', 'm', 'e'},
                new byte[] {'n', 'o', 't', 'e'},
                true);
        var invalid = valid.clone();
        var headerCrcOffset = headerLength(
                FEXTRA | FNAME | FCOMMENT,
                4,
                4,
                4);
        invalid[headerCrcOffset] ^= 0x01;

        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(valid));
        assertMalformed(
                invalid,
                SkillSavedDataPrimaryFailure.GzipFailureKind.FHCRC_INVALID);

        var extraOnly = customHeaderGzip(
                FEXTRA, new byte[] {5, 8, 13}, null, null, true);
        var nameOnly = customHeaderGzip(
                FNAME, null, new byte[] {'a'}, null, true);
        var commentOnly = customHeaderGzip(
                FCOMMENT, null, null, new byte[] {'b'}, true);
        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(extraOnly));
        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(nameOnly));
        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(commentOnly));
    }

    @Test
    void missingOptionalFieldTerminatorIsTruncatedWithoutAnUnboundedHeaderBuffer()
            throws Exception {
        var name = new byte[] {
                0x1f, (byte) 0x8b, 8, FNAME,
                0, 0, 0, 0, 0, (byte) 255,
                'n', 'o', 't', '-', 't', 'e', 'r', 'm', 'i', 'n', 'a', 't', 'e', 'd'
        };
        var comment = name.clone();
        comment[3] = FCOMMENT;

        assertMalformed(name, SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
        assertMalformed(comment, SkillSavedDataPrimaryFailure.GzipFailureKind.TRUNCATED);
    }

    @Test
    void b1MalformedTrailingAndSecondRootStayDecompressedCarrierFailures()
            throws Exception {
        assertDecompressedFailure(gzip(new byte[] {1, 2, 3}));
        var earlyMalformed = new byte[1_048_576];
        earlyMalformed[0] = 1;
        assertDecompressedFailure(gzip(earlyMalformed));
        var root = canonicalRoot();
        assertDecompressedFailure(gzip(concat(root, new byte[] {0})));
        assertDecompressedFailure(gzip(concat(root, root)));
    }

    @Test
    void decompressedMaximumPlusOneStopsAtTheB1CapacityLayer() throws Exception {
        var exact = gzipZeros(
                SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES);
        var plusOne = gzipZeros(
                (long) SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES + 1);

        var exactFailure = assertInstanceOf(
                SkillSavedDataPrimaryFailure.DecompressedCarrierFailure.class,
                failure(load(exact)));
        assertTrue(!(exactFailure.failure()
                instanceof SkillSavedDataCarrierFailure.DecompressedWholeRootCapacityExceeded));
        var plusOneFailure = assertInstanceOf(
                SkillSavedDataPrimaryFailure.DecompressedCarrierFailure.class,
                failure(load(plusOne)));
        assertInstanceOf(
                SkillSavedDataCarrierFailure.DecompressedWholeRootCapacityExceeded.class,
                plusOneFailure.failure());
    }

    @Test
    void closedChannelFailureWinsOverHeaderAndB1Classification() throws Exception {
        var path = temporaryDirectory.resolve("closed.dat");
        Files.write(path, new byte[] {0});
        var channel = FileChannel.open(path, StandardOpenOption.READ);
        channel.close();

        var failure = failure(StrictSingleMemberGzipInput.load(
                channel,
                1,
                MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES,
                Optional.empty()));

        var outer = assertInstanceOf(
                SkillSavedDataPrimaryFailure.OuterSavedDataUnreadable.class, failure);
        assertEquals(
                SkillSavedDataPrimaryFailure.PrimaryIngressStage.READ_CHANNEL,
                outer.stage());
    }

    @Test
    void nearFileLimitNameAndCommentRemainBoundedOrdinaryTests() throws Exception {
        var name = temporaryDirectory.resolve("near-limit-name.dat");
        var comment = temporaryDirectory.resolve("near-limit-comment.dat");
        writeNearLimitOptionalHeader(name, FNAME);
        writeNearLimitOptionalHeader(comment, FCOMMENT);

        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(name));
        assertInstanceOf(StrictSingleMemberGzipResult.Ready.class, load(comment));
    }

    private StrictSingleMemberGzipResult load(byte[] bytes) throws IOException {
        var path = temporaryDirectory.resolve("gzip-" + System.nanoTime() + ".dat");
        Files.write(path, bytes);
        return load(path);
    }

    private static StrictSingleMemberGzipResult load(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            return StrictSingleMemberGzipInput.load(
                    channel,
                    channel.size(),
                    MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES,
                    Optional.empty());
        }
    }

    private void assertFailure(byte[] bytes, Class<?> expected) throws IOException {
        assertInstanceOf(expected, failure(load(bytes)));
    }

    private void assertMalformed(
            byte[] bytes,
            SkillSavedDataPrimaryFailure.GzipFailureKind expected) throws IOException {
        var malformed = assertInstanceOf(
                SkillSavedDataPrimaryFailure.MalformedGzip.class,
                failure(load(bytes)));
        assertEquals(expected, malformed.kind());
    }

    private void assertDecompressedFailure(byte[] bytes) throws IOException {
        assertInstanceOf(
                SkillSavedDataPrimaryFailure.DecompressedCarrierFailure.class,
                failure(load(bytes)));
    }

    private static SkillSavedDataPrimaryFailure failure(
            StrictSingleMemberGzipResult result) {
        return assertInstanceOf(
                StrictSingleMemberGzipResult.Failure.class, result).failure();
    }

    private static byte[] canonicalGzip() {
        return gzip(canonicalRoot());
    }

    private static byte[] canonicalRoot() {
        return SkillSavedDataTestSupport.canonicalWholeRoot(
                SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]);
    }

    private static byte[] gzip(byte[] decompressed) {
        try {
            var output = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(output)) {
                gzip.write(decompressed);
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] gzipZeros(long count) {
        try {
            var output = new ByteArrayOutputStream();
            var block = new byte[8_192];
            try (var gzip = new GZIPOutputStream(output)) {
                var remaining = count;
                while (remaining > 0) {
                    var current = (int) Math.min(remaining, block.length);
                    gzip.write(block, 0, current);
                    remaining -= current;
                }
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] customHeaderGzip(
            int flags,
            byte[] extra,
            byte[] name,
            byte[] comment,
            boolean validHeaderCrc) {
        try {
            var canonical = canonicalGzip();
            var header = new ByteArrayOutputStream();
            header.write(new byte[] {
                    0x1f, (byte) 0x8b, 8, (byte) flags,
                    0, 0, 0, 0, 0, (byte) 255
            });
            if ((flags & FEXTRA) != 0) {
                var value = extra == null ? new byte[0] : extra;
                header.write(value.length & 0xff);
                header.write((value.length >>> 8) & 0xff);
                header.write(value);
            }
            if ((flags & FNAME) != 0) {
                header.write(name == null ? new byte[0] : name);
                header.write(0);
            }
            if ((flags & FCOMMENT) != 0) {
                header.write(comment == null ? new byte[0] : comment);
                header.write(0);
            }
            if ((flags & FHCRC) != 0) {
                var crc = new CRC32();
                crc.update(header.toByteArray());
                var low = (int) crc.getValue() & 0xffff;
                if (!validHeaderCrc) {
                    low ^= 1;
                }
                header.write(low & 0xff);
                header.write((low >>> 8) & 0xff);
            }
            header.write(canonical, 10, canonical.length - 10);
            return header.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static int headerLength(
            int flags,
            int extraLength,
            int nameLength,
            int commentLength) {
        var length = 10;
        if ((flags & FEXTRA) != 0) {
            length += 2 + extraLength;
        }
        if ((flags & FNAME) != 0) {
            length += nameLength + 1;
        }
        if ((flags & FCOMMENT) != 0) {
            length += commentLength + 1;
        }
        return length;
    }

    private static void writeNearLimitOptionalHeader(Path path, int flag) throws IOException {
        var canonical = canonicalGzip();
        var tailLength = canonical.length - 10;
        var optionalLength = MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES
                - 10 - 1 - tailLength;
        var header = new byte[] {
                0x1f, (byte) 0x8b, 8, (byte) flag,
                0, 0, 0, 0, 0, (byte) 255
        };
        var block = new byte[8_192];
        Arrays.fill(block, (byte) 'a');
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            writeFully(channel, ByteBuffer.wrap(header));
            var remaining = optionalLength;
            while (remaining > 0) {
                var current = Math.min(remaining, block.length);
                writeFully(channel, ByteBuffer.wrap(block, 0, current));
                remaining -= current;
            }
            writeFully(channel, ByteBuffer.wrap(new byte[] {0}));
            writeFully(channel, ByteBuffer.wrap(canonical, 10, tailLength));
        }
        assertEquals(
                MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES,
                Files.size(path));
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        var combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }
}
