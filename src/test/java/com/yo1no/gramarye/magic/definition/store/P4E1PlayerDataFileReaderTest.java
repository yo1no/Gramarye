package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class P4E1PlayerDataFileReaderTest {
    private static final UUID PLAYER_ID =
            UUID.fromString("01234567-89ab-cdef-8123-456789abcdef");
    private static final long ATTACHMENT_MAXIMUM = 16_777_216L;

    @TempDir
    Path temporaryDirectory;

    @Test
    void canonicalSingleMemberUsesSameChannelAndPublishesSelectedAttachment() throws Exception {
        var route = route();
        Files.write(route.primary(), gzip(playerRoot((byte) 7)));

        var selected = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        P4E1TestBudgets.create(),
                        P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM)));
        var scanned = (P4E1PlayerDataNbtScanner.ScanResult.Ready) selected.value();
        var attachment = assertInstanceOf(
                P4E1PlayerDataNbtScanner.AttachmentObservation.Present.class,
                scanned.attachment());

        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.PRIMARY, selected.source());
        assertEquals(ByteTag.valueOf((byte) 7), attachment.tag());
        assertEquals(2L, attachment.exactWriteAnyTagBytes());
    }

    @Test
    void secondMemberTrailingAndPaddingAreStrictAndNeverFallBackOld() throws Exception {
        var route = route();
        var member = gzip(playerRoot((byte) 1));
        Files.write(route.old(), gzip(playerRoot((byte) 9)));

        for (var invalid : new byte[][] {
                concatenate(member, gzip(playerRoot((byte) 2))),
                concatenate(member, new byte[] {0x55}),
                concatenate(member, new byte[] {0})
        }) {
            Files.write(route.primary(), invalid);
            var failure = assertInstanceOf(
                    P4E1PlayerDataSourceSelector.SelectionResult.Failure.class,
                    P4E1PlayerDataSourceSelector.select(
                            route,
                            P4E1TestBudgets.create(),
                            P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM)));
            assertEquals(P4E1SourceFailure.Code.STRICT_GZIP_REJECTED,
                    failure.failure().code());
        }
    }

    @Test
    void lockedCommonsTrailerFailuresAreStrictAndNeverOpenOld() throws Exception {
        var canonical = gzip(playerRoot((byte) 1));
        var crc = canonical.clone();
        crc[crc.length - 8] ^= 0x01;
        var isize = canonical.clone();
        isize[isize.length - 4] ^= 0x01;
        var truncatedTrailer = Arrays.copyOf(canonical, canonical.length - 1);
        var fixtures = new byte[][] {crc, isize, truncatedTrailer};

        for (var index = 0; index < fixtures.length; index++) {
            var directory = Files.createDirectory(temporaryDirectory.resolve("trailer-" + index));
            var route = route(directory);
            Files.write(route.primary(), fixtures[index]);
            Files.write(route.old(), gzip(playerRoot((byte) 9)));
            var access = new CountingAccess();

            var failure = assertInstanceOf(
                    P4E1PlayerDataSourceSelector.SelectionResult.Failure.class,
                    P4E1PlayerDataSourceSelector.select(
                            route,
                            P4E1TestBudgets.create(),
                            P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM),
                            access,
                            P4E1PlayerDataSourceSelector.Observer.NONE),
                    "trailer fixture " + index);

            assertEquals(P4E1SourceFailure.Code.STRICT_GZIP_REJECTED,
                    failure.failure().code());
            assertEquals(0, access.openCount(route.old()));
            assertEquals(0, access.attributeCount(route.old()));
        }
    }

    @Test
    void malformedHeaderAndTruncatedBodyRemainPlatformFallbackEligible() throws Exception {
        var canonical = gzip(playerRoot((byte) 1));
        var fixtures = new byte[][] {
            new byte[] {0x01, 0x02, 0x03},
            Arrays.copyOf(canonical, Math.max(11, canonical.length / 2))
        };

        for (var index = 0; index < fixtures.length; index++) {
            var directory = Files.createDirectory(temporaryDirectory.resolve("fallback-" + index));
            var route = route(directory);
            Files.write(route.primary(), fixtures[index]);
            Files.write(route.old(), gzip(playerRoot((byte) 9)));
            var access = new CountingAccess();

            var selected = assertInstanceOf(
                    P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                    P4E1PlayerDataSourceSelector.select(
                            route,
                            P4E1TestBudgets.create(),
                            P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM),
                            access,
                            P4E1PlayerDataSourceSelector.Observer.NONE));

            assertEquals(P4E1PlayerDataSourceSelector.SourceKind.OLD, selected.source());
            assertEquals(1, access.openCount(route.old()));
        }
    }

    @Test
    void aggregateCompressedCapacityFailureIsStrictAndDoesNotOpenOld() throws Exception {
        var route = route();
        var primary = gzip(playerRoot((byte) 1));
        Files.write(route.primary(), primary);
        Files.write(route.old(), gzip(playerRoot((byte) 9)));
        var budget = P4E1TestBudgets.create();
        var aggregateMaximum = budget.maximum(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL);
        var prefill = aggregateMaximum - primary.length + 1L;
        fillCompressedAggregateTo(budget, prefill);
        var access = new CountingAccess();

        var failure = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Failure.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        budget,
                        P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM),
                        access,
                        P4E1PlayerDataSourceSelector.Observer.NONE));

        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                failure.failure().code());
        assertEquals(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                failure.failure().counter().orElseThrow());
        assertEquals(P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                failure.failure().stage());
        assertEquals(aggregateMaximum + 1L, failure.failure().observedAtLeast());
        assertEquals(prefill, budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL));
        assertEquals(0, access.openCount(route.old()));
        assertEquals(0, access.attributeCount(route.old()));
    }

    @Test
    void provablyMalformedGzipFallsBackButPostNbtVersionFailureDoesNot() throws Exception {
        var route = route();
        Files.write(route.old(), gzip(playerRoot((byte) 9)));
        Files.write(route.primary(), new byte[] {0x01, 0x02, 0x03});

        var old = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        P4E1TestBudgets.create(),
                        P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM)));
        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.OLD, old.source());

        var wrongVersion = playerRoot((byte) 3);
        wrongVersion.putInt("DataVersion", 3_954);
        Files.write(route.primary(), gzip(wrongVersion));
        var failure = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Failure.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        P4E1TestBudgets.create(),
                        P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM)));
        assertEquals(P4E1SourceFailure.Code.DATA_VERSION_NOT_CURRENT,
                failure.failure().code());
    }

    @Test
    void platformSkippedWrongOuterSelectsPrimaryMissingAndDoesNotReadOld() throws Exception {
        var route = route(Files.createDirectory(temporaryDirectory.resolve("attachment")));
        var wrongOuter = new CompoundTag();
        wrongOuter.putInt(
                "DataVersion", P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
        wrongOuter.putString(
                P4E1PlayerDataNbtScanner.ATTACHMENTS_FIELD, "wrong-type");
        Files.write(route.primary(), gzip(wrongOuter));
        Files.write(route.old(), gzip(playerRoot((byte) 9)));
        var access = new CountingAccess();

        var selected = assertInstanceOf(
                P4E1PlayerDataSourceSelector.SelectionResult.Ready.class,
                P4E1PlayerDataSourceSelector.select(
                        route,
                        P4E1TestBudgets.create(),
                        P4E1PlayerDataFileReader.reader(ATTACHMENT_MAXIMUM),
                        access,
                        P4E1PlayerDataSourceSelector.Observer.NONE));

        assertEquals(P4E1PlayerDataSourceSelector.SourceKind.PRIMARY, selected.source());
        var scanned = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                selected.value());
        assertEquals(
                P4E1PlayerDataNbtScanner.AttachmentObservation.Missing.INSTANCE,
                scanned.attachment());
        assertEquals(0, access.attributeCount(route.old()));
        assertEquals(0, access.openCount(route.old()));
    }

    private P4E1PlayerDataDirectorySnapshot.RouteRecord route() {
        return route(temporaryDirectory);
    }

    private static P4E1PlayerDataDirectorySnapshot.RouteRecord route(Path directory) {
        var name = PLAYER_ID.toString();
        return new P4E1PlayerDataDirectorySnapshot.RouteRecord(
                PLAYER_ID,
                directory.resolve(name + ".dat"),
                directory.resolve(name + ".dat_old"));
    }

    private static CompoundTag playerRoot(byte selected) {
        var root = new CompoundTag();
        root.putInt("DataVersion", P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
        var attachments = new CompoundTag();
        attachments.put(
                P4E1PlayerDataNbtScanner.PLAYER_SKILLS_FIELD,
                ByteTag.valueOf(selected));
        root.put(P4E1PlayerDataNbtScanner.ATTACHMENTS_FIELD, attachments);
        return root;
    }

    private static byte[] gzip(CompoundTag root) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(bytes);
                var output = new DataOutputStream(gzip)) {
            NbtIo.writeUnnamedTag(root, output);
        }
        return bytes.toByteArray();
    }

    private static byte[] concatenate(byte[] first, byte[] second) {
        var combined = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, combined, first.length, second.length);
        return combined;
    }

    private static void fillCompressedAggregateTo(P4E1AuditBudget budget, long target) {
        var perFileMaximum = budget.maximum(P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE);
        while (budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL) < target) {
            var remaining = target
                    - budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL);
            var scope = budget.newFileScope();
            assertTrue(scope.checkpointFileAndAggregate(
                            P4E1AuditCounter.COMPRESSED_BYTES_PER_FILE,
                            P4E1AuditCounter.COMPRESSED_BYTES_TOTAL,
                            P4E1AuditStage.PER_FILE_COMPRESSED,
                            P4E1AuditStage.AGGREGATE_COMPRESSED_CHECKED_ADD,
                            Math.min(perFileMaximum, remaining))
                    .isEmpty());
        }
    }

    private static final class CountingAccess extends P4E1FileSystemAccess {
        private final Map<Path, Integer> attributes = new HashMap<>();
        private final Map<Path, Integer> opens = new HashMap<>();

        @Override
        public BasicFileAttributes readAttributes(Path path) throws IOException {
            attributes.merge(path, 1, Integer::sum);
            return P4E1FileSystemAccess.SYSTEM.readAttributes(path);
        }

        @Override
        public DirectoryStream<Path> openDirectory(Path directory) throws IOException {
            return P4E1FileSystemAccess.SYSTEM.openDirectory(directory);
        }

        @Override
        public FileChannel openRead(Path path) throws IOException {
            opens.merge(path, 1, Integer::sum);
            return P4E1FileSystemAccess.SYSTEM.openRead(path);
        }

        private int attributeCount(Path path) {
            return attributes.getOrDefault(path, 0);
        }

        private int openCount(Path path) {
            return opens.getOrDefault(path, 0);
        }
    }
}
