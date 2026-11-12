package com.yo1no.gramarye.magic.definition.scoped;

import com.yo1no.gramarye.magic.api.id.SkillOwnerId;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.MAX_CATALOG_PAYLOAD;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDLimits.MAX_DRAFT_PAYLOAD;
import static com.yo1no.gramarye.magic.definition.scoped.ScopeDStorage.Reason;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopeDStorageTest {
    private static final SkillOwnerId OWNER = new SkillOwnerId(UUID.fromString("02635b21-2d26-4606-8347-61a413e25e78"));
    private static final String CONTEXT = "a5".repeat(32);
    private static final byte[] FIRST = {11, 12, 13};
    private static final byte[] NEXT = {21, 22, 23};
    private static final int HALTED = 91;

    @TempDir
    private Path root;

    @Test
    void absenceAndPartialStagePreserveOldCurrentAndBurnReservedSerial() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            assertEquals(0, storage.current().generation());
            assertFalse(Files.exists(path.resolve("catalog.current")));
            assertFalse(storage.hasPendingMaterial());
            assertTrue(storage.readDrafts().isEmpty());
            publish(storage, FIRST);
        }

        runChild(path, "partial-stage", HALTED);
        try (var recovered = ScopeDStorage.open(path, OWNER)) {
            assertEquals(1, recovered.current().generation());
            assertArrayEquals(FIRST, recovered.current().payload());
            assertTrue(recovered.hasPendingMaterial());
            assertTrue(recovered.pending().isEmpty());
            assertEquals(Reason.PENDING, assertThrows(ScopeDStorage.Failure.class, recovered::reserve).reason());
            recovered.discardInterruptedStage(recovered.current());
            assertEquals(3, recovered.reserve());
        }
    }

    @Test
    void completePrepareRecoversMatchingBaseWithoutPrematurePublication() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        ScopeDStorage.Image before;
        UUID source;
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            before = publish(storage, FIRST);
            source = storage.sourceId();
        }
        runChild(path, "prepared", HALTED);

        try (var recovered = ScopeDStorage.open(path, OWNER)) {
            var base = recovered.current();
            var prepared = recovered.pending().orElseThrow();
            assertEquals(source, recovered.sourceId());
            assertEquals(before.digest(), base.digest());
            assertEquals(base.digest(), prepared.baseDigest());
            assertEquals(base.generation(), prepared.baseGeneration());
            assertEquals(2, prepared.transaction());
            assertEquals(CONTEXT, prepared.contextDigest());
            assertArrayEquals(NEXT, prepared.payload());
            assertArrayEquals(FIRST, recovered.current().payload());
            assertEquals(Reason.STALE,
                    assertThrows(ScopeDStorage.Failure.class, () -> recovered.commitPreparedImage(before, prepared)).reason());
            assertSame(prepared, recovered.commitPreparedImage(base, prepared));
            assertArrayEquals(NEXT, recovered.current().payload());
        }
        try (var recoveredAgain = ScopeDStorage.open(path, OWNER)) {
            assertEquals(2, recoveredAgain.current().generation());
            assertEquals(2, recoveredAgain.current().transaction());
            assertFalse(recoveredAgain.hasPendingMaterial());
            assertEquals(3, recoveredAgain.reserve());
        }
    }

    @Test
    void restartAfterPhysicalMoveAndAfterDurableDecisionBeforeRamOrAck() throws Exception {
        for (String scenario : new String[]{"moved", "durable"}) {
            Path path = Files.createDirectory(root.resolve(scenario));
            try (var storage = ScopeDStorage.open(path, OWNER)) {
                publish(storage, FIRST);
            }
            runChild(path, scenario, HALTED);

            // Process exit is a real interruption, not a power-cut simulation. Opening re-forces directory metadata.
            try (var recovered = ScopeDStorage.open(path, OWNER)) {
                assertEquals(2, recovered.current().generation());
                assertEquals(2, recovered.current().transaction());
                assertArrayEquals(NEXT, recovered.current().payload());
                assertFalse(recovered.hasPendingMaterial());
            }
        }
    }

    @Test
    void writerExclusivitySurvivesFailedSameJvmAcquisitionAndReleasesOnClose() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        var storage = ScopeDStorage.open(path, OWNER);
        try (storage) {
            var occupied = assertThrows(ScopeDStorage.Failure.class, () -> ScopeDStorage.open(path, OWNER));
            assertEquals(Reason.UNAVAILABLE, occupied.reason());
            runChild(path, "locked", 0);
            publish(storage, FIRST);
        }
        try (var replacement = ScopeDStorage.open(path, OWNER)) {
            storage.close();
            assertEquals(1, replacement.current().generation());
            assertThrows(ScopeDStorage.Failure.class, storage::current);
            runChild(path, "locked", 0);
        }
    }

    @Test
    void failedPostMoveReadbackFencesAndPreservesCommittedEvidenceForRecovery() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            publish(storage, FIRST);
        }
        runChild(path, "failed-readback", HALTED);
        assertQuarantined(path, OWNER);
        assertTrue(Files.isRegularFile(path.resolve("catalog.current")));
        assertTrue(Files.isRegularFile(path.resolve("allocation.current")));
    }

    @Test
    void exactDispositionDoesNotReleaseTheDurablyReservedSerial() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        ScopeDStorage.Image obsolete;
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            obsolete = storage.stage(storage.current(), storage.reserve(), CONTEXT, NEXT);
        }
        try (var reopened = ScopeDStorage.open(path, OWNER)) {
            var base = reopened.current();
            var pending = reopened.pending().orElseThrow();
            assertEquals(Reason.STALE,
                    assertThrows(ScopeDStorage.Failure.class, () -> reopened.discardPending(base, obsolete)).reason());
            reopened.discardPending(base, pending);
            assertFalse(reopened.hasPendingMaterial());
            assertEquals(2, reopened.reserve());
            assertEquals(0, reopened.current().generation());
        }
    }

    @Test
    void currentLossCorruptionWrongOwnerAndMixedSourceNeverFallback() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        Path other = Files.createDirectory(root.resolve("other"));
        try (var storage = ScopeDStorage.open(path, OWNER); var another = ScopeDStorage.open(other, OWNER)) {
            assertNotEquals(storage.sourceId(), another.sourceId());
            publish(storage, FIRST);
            publish(another, NEXT);
        }

        Path current = path.resolve("catalog.current");
        byte[] original = Files.readAllBytes(current);
        Files.copy(other.resolve("catalog.current"), current, StandardCopyOption.REPLACE_EXISTING);
        assertQuarantined(path, OWNER);
        Files.write(current, original);
        assertQuarantined(path, new SkillOwnerId(new UUID(0, 3)));
        Files.write(current, Arrays.copyOf(original, original.length - 1));
        assertQuarantined(path, OWNER);
        Files.delete(current);
        assertQuarantined(path, OWNER);
        assertFalse(Files.exists(current));
    }

    @Test
    void unusableDraftDoesNotReplaceOrFenceCoherentCatalog() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            publish(storage, FIRST);
            byte[] workingCopy = {4, 3, 2, 1};
            storage.writeDrafts(workingCopy);
            workingCopy[0] = 99;
            byte[] read = storage.readDrafts().orElseThrow();
            assertArrayEquals(new byte[]{4, 3, 2, 1}, read);
            read[0] = 99;
            assertEquals(4, storage.readDrafts().orElseThrow()[0]);
        }
        Files.write(path.resolve("draft.current"), new byte[]{0});
        try (var recovered = ScopeDStorage.open(path, OWNER)) {
            assertThrows(ScopeDStorage.Failure.class, recovered::readDrafts);
            assertThrows(ScopeDStorage.Failure.class, () -> recovered.writeDrafts(NEXT));
            assertEquals(1, recovered.current().generation());
            publish(recovered, NEXT);
            assertEquals(2, recovered.current().generation());
        }
    }

    @Test
    void preparedWrongPredecessorIsPreservedAndBlocksOtherWork() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        byte[] generationOne;
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            publish(storage, FIRST);
            generationOne = Files.readAllBytes(path.resolve("catalog.current"));
            publish(storage, NEXT);
            storage.stage(storage.current(), storage.reserve(), CONTEXT, FIRST);
        }
        Files.write(path.resolve("catalog.current"), generationOne);
        assertQuarantined(path, OWNER);
        assertTrue(Files.isRegularFile(path.resolve("catalog.prepare")));
    }

    @Test
    void losingDurableAllocationAfterReservationQuarantinesEvenAtGenesis() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            assertEquals(1, storage.reserve());
            assertEquals(0, storage.current().generation());
        }
        Files.delete(path.resolve("allocation.current"));
        assertQuarantined(path, OWNER);
    }

    @Test
    void durableSerialAtTechnicalMaximumRejectsSuccessorWithoutWriting() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        try (var storage = ScopeDStorage.open(path, OWNER)) {
            assertEquals(1, storage.reserve());
        }
        Path watermark = path.resolve("allocation.current");
        byte[] record = Files.readAllBytes(watermark);
        int checksumAt = record.length - 32;
        ByteBuffer.wrap(record).putLong(checksumAt - Long.BYTES, ScopeDLimits.MAX_SERIAL);
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        hash.update(record, 0, checksumAt);
        System.arraycopy(hash.digest(), 0, record, checksumAt, 32);
        Files.write(watermark, record);
        try (var recovered = ScopeDStorage.open(path, OWNER)) {
            assertEquals(Reason.OVERFLOW, assertThrows(ScopeDStorage.Failure.class, recovered::reserve).reason());
            assertEquals(0, recovered.current().generation());
        }
        assertArrayEquals(record, Files.readAllBytes(watermark));
    }

    @Test
    void maxAndMaxPlusOnePhysicalBytesInFixedHeapChild() throws Exception {
        Path path = Files.createDirectory(root.resolve("owner"));
        runChild(path, "max", 0);
        assertEquals(ScopeDLimits.MAX_CATALOG_BYTES, Files.size(path.resolve("catalog.current")));
        assertEquals(ScopeDLimits.MAX_DRAFT_BYTES, Files.size(path.resolve("draft.current")));
        assertFalse(Files.exists(path.resolve("catalog.prepare")));
    }

    private static void assertQuarantined(Path path, SkillOwnerId owner) {
        assertEquals(Reason.CORRUPT, assertThrows(ScopeDStorage.Failure.class,
                () -> ScopeDStorage.open(path, owner)).reason());
    }

    private void runChild(Path path, String mode, int exitCode) throws Exception {
        Path output = root.resolve("child-" + path.getFileName() + "-" + mode + "-" + UUID.randomUUID() + ".log");
        var child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xms24m", "-Xmx24m", "-XX:+ExitOnOutOfMemoryError", "-cp", System.getProperty("java.class.path"),
                Child.class.getName(), path.toString(), mode).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!child.waitFor(30, TimeUnit.SECONDS)) {
            child.destroyForcibly();
            assertTrue(child.waitFor(5, TimeUnit.SECONDS));
            throw new AssertionError("physical storage child timed out: " + mode);
        }
        assertEquals(exitCode, child.exitValue(), () -> {
            try (var input = Files.newInputStream(output)) {
                return new String(input.readNBytes(8192), java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    private static ScopeDStorage.Image publish(ScopeDStorage storage, byte[] body) throws IOException {
        var base = storage.current();
        var staged = storage.stage(base, storage.reserve(), CONTEXT, body);
        return storage.commitPreparedImage(base, staged);
    }

    /** Test-source-only subprocess, with no hooks or environment toggles in the shipped adapter. */
    public static final class Child {
        public static void main(String[] args) throws Exception {
            Path directory = Path.of(args[0]);
            String mode = args[1];
            if (mode.equals("locked")) {
                assertEquals(Reason.UNAVAILABLE, assertThrows(ScopeDStorage.Failure.class,
                        () -> ScopeDStorage.open(directory, OWNER)).reason());
                return;
            }
            try (var storage = ScopeDStorage.open(directory, OWNER)) {
                if (mode.equals("max")) {
                    maximumImages(storage, directory);
                    return;
                }
                var base = storage.current();
                long serial = storage.reserve();
                if (mode.equals("partial-stage")) {
                    // Model the incomplete scratch bytes left by interruption before the prepare rename.
                    Files.write(directory.resolve("catalog.writing"), new byte[]{1, 2, 3}, StandardOpenOption.CREATE_NEW);
                } else {
                    var staged = storage.stage(base, serial, CONTEXT, NEXT);
                    assertSame(base, storage.current());
                    if (!mode.equals("prepared")) {
                        // Stop between real private physical callsites, before the public operation installs RAM state.
                        invoke("beginCatalogReplacement", storage, base, staged);
                        assertEquals(Reason.FENCED, assertThrows(ScopeDStorage.Failure.class, storage::current).reason());
                        if (mode.equals("durable")) {
                            invoke("finishCatalogReplacement", storage, staged);
                            assertEquals(Reason.FENCED, assertThrows(ScopeDStorage.Failure.class, storage::reserve).reason());
                        } else if (mode.equals("failed-readback")) {
                            Files.write(directory.resolve("catalog.current"), new byte[]{9});
                            var readbackFailure = assertThrows(InvocationTargetException.class,
                                    () -> invoke("finishCatalogReplacement", storage, staged));
                            assertEquals(Reason.FENCED, ((ScopeDStorage.Failure) readbackFailure.getCause()).reason());
                            assertEquals(Reason.FENCED, assertThrows(ScopeDStorage.Failure.class, storage::current).reason());
                        } else {
                            assertEquals("moved", mode);
                        }
                    }
                }
                Runtime.getRuntime().halt(HALTED);
            }
        }

        private static void maximumImages(ScopeDStorage storage, Path directory) throws IOException {
            assertTrue(Runtime.getRuntime().maxMemory() <= 24 * 1024 * 1024L);
            byte[] body = new byte[MAX_CATALOG_PAYLOAD];
            Arrays.fill(body, (byte) 5);
            publish(storage, body);
            var base = storage.current();
            long serial = storage.reserve();
            assertEquals(Reason.OVERFLOW, assertThrows(ScopeDStorage.Failure.class,
                    () -> storage.stage(base, serial, CONTEXT, new byte[MAX_CATALOG_PAYLOAD + 1])).reason());
            assertFalse(storage.hasPendingMaterial());
            body[body.length - 1] = 9;
            var prepared = storage.stage(base, serial, CONTEXT, body);
            assertEquals(ScopeDLimits.MAX_CATALOG_BYTES, Files.size(directory.resolve("catalog.current")));
            assertEquals(ScopeDLimits.MAX_CATALOG_BYTES, Files.size(directory.resolve("catalog.prepare")));
            assertEquals(5, base.payload()[body.length - 1]);
            assertEquals(9, storage.commitPreparedImage(base, prepared).payload()[body.length - 1]);
            assertEquals(Reason.OVERFLOW, assertThrows(ScopeDStorage.Failure.class,
                    () -> storage.writeDrafts(new byte[MAX_DRAFT_PAYLOAD + 1])).reason());
            storage.writeDrafts(new byte[MAX_DRAFT_PAYLOAD]);
            assertEquals(MAX_DRAFT_PAYLOAD, storage.readDrafts().orElseThrow().length);
        }

        private static void invoke(String name, ScopeDStorage storage, ScopeDStorage.Image... images) throws Exception {
            Class<?>[] parameters = new Class<?>[images.length];
            Arrays.fill(parameters, ScopeDStorage.Image.class);
            Method method = ScopeDStorage.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            method.invoke(storage, (Object[]) images);
        }
    }
}
