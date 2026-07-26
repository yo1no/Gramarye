package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillSavedDataPrimaryIngressTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void absentRequiresTwoNoSuchFileObservations() throws Exception {
        var primary = temporaryDirectory.resolve("gramarye_skill_definitions.dat");

        assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Absent.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));

        var appeared = SkillSavedDataPrimaryIngress.load(
                primary,
                Optional.empty(),
                new SkillSavedDataPrimaryIngress.PrimaryIngressObserver() {
                    @Override
                    public void afterFirstAbsentCheck() {
                        writeUnchecked(primary, canonicalGzip());
                    }
                });
        var race = assertInstanceOf(
                SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected.class,
                failure(appeared));
        assertTrue(race.kind()
                == SkillSavedDataPrimaryFailure.PrimaryFileRaceKind
                        .APPEARED_AFTER_ABSENT_CHECK);
    }

    @Test
    void symlinkDirectoryAndUnixSocketFailClosedByFileKind() throws Exception {
        var target = temporaryDirectory.resolve("target.dat");
        Files.write(target, canonicalGzip());
        var symlink = temporaryDirectory.resolve("symlink.dat");
        Files.createSymbolicLink(symlink, target.getFileName());

        assertKind(symlink, SkillSavedDataPrimaryFailure.PrimaryFileKind.SYMBOLIC_LINK);
        assertKind(temporaryDirectory, SkillSavedDataPrimaryFailure.PrimaryFileKind.DIRECTORY);

        var socket = temporaryDirectory.resolve("primary.socket");
        try (var server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            assertKind(socket, SkillSavedDataPrimaryFailure.PrimaryFileKind.OTHER);
        } finally {
            Files.deleteIfExists(socket);
        }
    }

    @Test
    void defaultFilesystemProvidesIdentityAndNullIdentityFixtureFailsClosed()
            throws Exception {
        var primary = temporaryDirectory.resolve("identity.dat");
        Files.write(primary, canonicalGzip());
        var local = PrimaryFileMetadata.capture(Files.readAttributes(
                primary, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS));

        assertNotNull(local.fileKey());

        var nullKey = PrimaryFileMetadata.capture(new NullKeyRegularAttributes(1));
        assertInstanceOf(
                SkillSavedDataPrimaryFailure.PrimaryFileIdentityUnavailable.class,
                failure(SkillSavedDataPrimaryIngress.classifyPresent(nullKey)));
    }

    @Test
    void compressedFileCeilingIsInclusiveAndPlusOneStopsBeforeGzipParsing()
            throws Exception {
        var exact = temporaryDirectory.resolve("exact.dat");
        createSparseFile(exact, MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES);
        var exactFailure = failure(
                SkillSavedDataPrimaryIngress.load(exact, Optional.empty()));

        assertFalse(exactFailure
                instanceof SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded);

        var plusOne = temporaryDirectory.resolve("plus-one.dat");
        createSparseFile(
                plusOne,
                (long) MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES + 1);
        var capacity = assertInstanceOf(
                SkillSavedDataPrimaryFailure.SavedDataFileCapacityExceeded.class,
                failure(SkillSavedDataPrimaryIngress.load(plusOne, Optional.empty())));
        assertTrue(capacity.observedAtLeast()
                == (long) MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_FILE_BYTES + 1);
    }

    @Test
    void growShrinkAndReplacementAfterPostOpenChecksAreTypedRaces() throws Exception {
        var grow = temporaryDirectory.resolve("grow.dat");
        Files.write(grow, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        grow,
                        Optional.empty(),
                        observerAfterPostOpen(() -> appendUnchecked(grow, (byte) 0))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.GREW_DURING_READ);

        var shrink = temporaryDirectory.resolve("shrink.dat");
        Files.write(shrink, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        shrink,
                        Optional.empty(),
                        observerAfterPostOpen(() -> truncateUnchecked(
                                shrink, sizeUnchecked(shrink) - 1))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.SHRANK_DURING_READ);

        var replaced = temporaryDirectory.resolve("replaced.dat");
        var replacement = temporaryDirectory.resolve("replacement.tmp");
        Files.write(replaced, canonicalGzip());
        Files.write(replacement, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        replaced,
                        Optional.empty(),
                        observerAfterPostOpen(() -> moveUnchecked(replacement, replaced))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED);
    }

    @Test
    void growShrinkAndReplacementBetweenBaselineAndOpenAreTypedRaces() throws Exception {
        var grow = temporaryDirectory.resolve("pre-open-grow.dat");
        Files.write(grow, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        grow,
                        Optional.empty(),
                        observerAfterInitialMetadata(() -> appendUnchecked(grow, (byte) 0))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.GREW_DURING_READ);

        var shrink = temporaryDirectory.resolve("pre-open-shrink.dat");
        Files.write(shrink, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        shrink,
                        Optional.empty(),
                        observerAfterInitialMetadata(() -> truncateUnchecked(
                                shrink, sizeUnchecked(shrink) - 1))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.SHRANK_DURING_READ);

        var replaced = temporaryDirectory.resolve("pre-open-replaced.dat");
        var replacement = temporaryDirectory.resolve("pre-open-replacement.tmp");
        Files.write(replaced, canonicalGzip());
        Files.write(replacement, canonicalGzip());
        assertRace(
                SkillSavedDataPrimaryIngress.load(
                        replaced,
                        Optional.empty(),
                        observerAfterInitialMetadata(
                                () -> moveUnchecked(replacement, replaced))),
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.REPLACED);
    }

    @Test
    void finalMetadataAndChannelSizeCheckWinsAfterSuccessfulParse() throws Exception {
        var primary = temporaryDirectory.resolve("post-parse-grow.dat");
        Files.write(primary, canonicalGzip());

        var result = SkillSavedDataPrimaryIngress.load(
                primary,
                Optional.empty(),
                new SkillSavedDataPrimaryIngress.PrimaryIngressObserver() {
                    @Override
                    public void afterParse() {
                        appendUnchecked(primary, (byte) 0);
                    }
                });

        assertRace(
                result,
                SkillSavedDataPrimaryFailure.PrimaryFileRaceKind.GREW_DURING_READ);
    }

    @Test
    void canonicalPrimaryLoadsReadyFromTheSingleOpenedChannel() throws Exception {
        var primary = temporaryDirectory.resolve("gramarye_skill_definitions.dat");
        Files.write(primary, canonicalGzip());

        var ready = assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Ready.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));

        assertFalse(ready.candidate().rewriteRequired());
        assertTrue(ready.candidate().store().snapshot().histories().isEmpty());
    }

    @Test
    void datOldNeverActsAsFallbackAndPrimaryBytesRemainUntouched() throws Exception {
        var primary = temporaryDirectory.resolve("gramarye_skill_definitions.dat");
        var old = temporaryDirectory.resolve("gramarye_skill_definitions.dat_old");
        Files.write(old, canonicalGzip());

        assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Absent.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));

        var invalidPrimary = new byte[] {31, (byte) 139, 8};
        Files.write(primary, invalidPrimary);
        var before = Files.readAllBytes(primary);
        assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Failure.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));
        assertArrayEquals(before, Files.readAllBytes(primary));

        Files.write(primary, canonicalGzip());
        Files.write(old, new byte[] {7, 7, 7});
        assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Ready.class,
                SkillSavedDataPrimaryIngress.load(primary, Optional.empty()));
    }

    private static SkillSavedDataPrimaryIngress.PrimaryIngressObserver observerAfterPostOpen(
            Runnable action) {
        return new SkillSavedDataPrimaryIngress.PrimaryIngressObserver() {
            @Override
            public void afterPostOpenChecks() {
                action.run();
            }
        };
    }

    private static SkillSavedDataPrimaryIngress.PrimaryIngressObserver observerAfterInitialMetadata(
            Runnable action) {
        return new SkillSavedDataPrimaryIngress.PrimaryIngressObserver() {
            @Override
            public void afterInitialMetadata() {
                action.run();
            }
        };
    }

    private static SkillSavedDataPrimaryFailure failure(
            SkillSavedDataPrimaryLoadResult result) {
        return assertInstanceOf(
                SkillSavedDataPrimaryLoadResult.Failure.class, result).failure();
    }

    private static void assertKind(
            Path path,
            SkillSavedDataPrimaryFailure.PrimaryFileKind expected) {
        var unsupported = assertInstanceOf(
                SkillSavedDataPrimaryFailure.UnsupportedPrimaryFileType.class,
                failure(SkillSavedDataPrimaryIngress.load(path, Optional.empty())));
        assertTrue(unsupported.kind() == expected);
    }

    private static void assertRace(
            SkillSavedDataPrimaryLoadResult result,
            SkillSavedDataPrimaryFailure.PrimaryFileRaceKind expected) {
        var race = assertInstanceOf(
                SkillSavedDataPrimaryFailure.PrimaryFileRaceDetected.class,
                failure(result));
        assertTrue(race.kind() == expected);
    }

    private static byte[] canonicalGzip() {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var gzip = new GZIPOutputStream(bytes)) {
                gzip.write(SkillSavedDataTestSupport.canonicalWholeRoot(
                        SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]));
            }
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void createSparseFile(Path path, long size) throws IOException {
        try (var channel = FileChannel.open(
                path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            channel.position(size - 1);
            channel.write(ByteBuffer.wrap(new byte[] {0}));
        }
    }

    private static void writeUnchecked(Path path, byte[] bytes) {
        try {
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void appendUnchecked(Path path, byte value) {
        try {
            Files.write(path, new byte[] {value}, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void truncateUnchecked(Path path, long size) {
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.truncate(size);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static long sizeUnchecked(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void moveUnchecked(Path source, Path target) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private record NullKeyRegularAttributes(long size) implements BasicFileAttributes {
        @Override
        public FileTime lastModifiedTime() {
            return FileTime.fromMillis(1);
        }

        @Override
        public FileTime lastAccessTime() {
            return FileTime.fromMillis(1);
        }

        @Override
        public FileTime creationTime() {
            return FileTime.fromMillis(1);
        }

        @Override
        public boolean isRegularFile() {
            return true;
        }

        @Override
        public boolean isDirectory() {
            return false;
        }

        @Override
        public boolean isSymbolicLink() {
            return false;
        }

        @Override
        public boolean isOther() {
            return false;
        }

        @Override
        public Object fileKey() {
            return null;
        }
    }
}
