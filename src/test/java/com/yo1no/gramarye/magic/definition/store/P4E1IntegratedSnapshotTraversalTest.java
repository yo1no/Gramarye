package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class P4E1IntegratedSnapshotTraversalTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000017");

    @TempDir
    Path temporaryDirectory;

    @Test
    void fourStateSelectionAndRelevantRecordCheckpointAreExact() {
        var snapshot = new CompoundTag();
        var server = new Object();

        var integratedBudget = P4E1TestBudgets.create();
        var integrated = P4E1IntegratedSnapshotTraversal.capture(
                new MutableAccess(server, true, OWNER, snapshot), integratedBudget);
        var selected = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class, integrated);
        assertEquals(OWNER, selected.ownerId());
        assertEquals(1L, integratedBudget.observed(P4E1AuditCounter.RELEVANT_RECORDS));

        var profileOnly = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Disk.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(server, true, OWNER, null),
                        P4E1TestBudgets.create()));
        assertEquals(OWNER, profileOnly.profileId().orElseThrow());

        var neither = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Disk.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(server, true, null, null),
                        P4E1TestBudgets.create()));
        assertTrue(neither.profileId().isEmpty());

        var identityFailure = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Failure.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(server, true, null, snapshot),
                        P4E1TestBudgets.create()));
        assertEquals(
                P4E1SourceFailure.Code.INTEGRATED_OWNER_IDENTITY_UNAVAILABLE,
                identityFailure.failure().code());
    }

    @Test
    void globalSelectionDefersTheSoleRelevantRecordCheckpoint() {
        var budget = P4E1TestBudgets.create();
        var selected = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.captureForGlobal(
                        new MutableAccess(new Object(), true, OWNER, new CompoundTag()),
                        budget));

        assertEquals(OWNER, selected.ownerId());
        assertEquals(0L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
    }

    @Test
    void integratedOwnerDiskPairCountsDirectoryButIsNotASecondRelevantRecord()
            throws IOException {
        var otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000018");
        Files.createFile(temporaryDirectory.resolve(OWNER + ".dat"));
        Files.createFile(temporaryDirectory.resolve(OWNER + ".dat_old"));
        Files.createFile(temporaryDirectory.resolve(otherOwner + ".dat"));

        var budget = P4E1TestBudgets.create();
        var directory = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(temporaryDirectory, budget))
                .snapshot();
        var selected = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(new Object(), true, OWNER, new CompoundTag()),
                        budget));
        var diskRecords = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Ready.class,
                directory.selectRecords(Optional.of(selected.ownerId()), budget)).records();

        assertEquals(3L, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
        assertEquals(2L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
        assertEquals(1, diskRecords.size());
        assertEquals(otherOwner, diskRecords.getFirst().playerId());
        assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.VerificationResult.Unchanged.class,
                directory.verifyUnchanged());
    }

    @Test
    void integratedOwnerExclusionMakesTheExactRelevantLimitInclusive() throws IOException {
        for (var index = 0; index < 2_048; index++) {
            Files.createFile(temporaryDirectory.resolve(new UUID(0L, index + 1L) + ".dat"));
        }

        var budget = P4E1TestBudgets.create();
        var directory = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(temporaryDirectory, budget))
                .snapshot();
        var integrated = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(new Object(), true, OWNER, new CompoundTag()),
                        budget));
        var diskRecords = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Ready.class,
                directory.selectRecords(Optional.of(integrated.ownerId()), budget)).records();

        assertEquals(2_048L, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
        assertEquals(2_048L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
        assertEquals(2_047, diskRecords.size());
        assertFalse(diskRecords.stream().anyMatch(record -> OWNER.equals(record.playerId())));
    }

    @Test
    void integratedOwnerExclusionStillRejectsTheNextRelevantRecord() throws IOException {
        for (var index = 0; index < 2_049; index++) {
            Files.createFile(temporaryDirectory.resolve(new UUID(0L, index + 1L) + ".dat"));
        }

        var budget = P4E1TestBudgets.create();
        var directory = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(temporaryDirectory, budget))
                .snapshot();
        var integrated = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(new Object(), true, OWNER, new CompoundTag()),
                        budget));
        var failed = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Failure.class,
                directory.selectRecords(Optional.of(integrated.ownerId()), budget));

        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                failed.failure().code());
        assertEquals(P4E1AuditCounter.RELEVANT_RECORDS,
                failed.failure().counter().orElseThrow());
        assertEquals(2_049L, failed.failure().observedAtLeast());
        assertEquals(2_048L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
    }

    @Test
    void profileWithoutRuntimeSnapshotDoesNotExcludeItsDiskPair() throws IOException {
        var otherOwner = UUID.fromString("00000000-0000-0000-0000-000000000018");
        Files.createFile(temporaryDirectory.resolve(OWNER + ".dat"));
        Files.createFile(temporaryDirectory.resolve(otherOwner + ".dat"));

        var budget = P4E1TestBudgets.create();
        var directory = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.CaptureResult.Ready.class,
                P4E1PlayerDataDirectorySnapshot.capture(temporaryDirectory, budget))
                .snapshot();
        var disk = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Disk.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(new Object(), true, OWNER, null), budget));
        var records = assertInstanceOf(
                P4E1PlayerDataDirectorySnapshot.RecordSelection.Ready.class,
                directory.selectRecords(Optional.empty(), budget)).records();

        assertEquals(OWNER, disk.profileId().orElseThrow());
        assertEquals(2L, budget.observed(P4E1AuditCounter.DIRECTORY_ENTRIES));
        assertEquals(2L, budget.observed(P4E1AuditCounter.RELEVANT_RECORDS));
        assertEquals(List.of(OWNER, otherOwner),
                records.stream().map(
                        P4E1PlayerDataDirectorySnapshot.RouteRecord::playerId).toList());
    }

    @Test
    void captureAndFreshnessRequireTheSameServerThreadProfileAndTagIdentity() {
        var server = new Object();
        var snapshot = new CompoundTag();
        var access = new MutableAccess(server, true, OWNER, snapshot);
        var selected = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(access, P4E1TestBudgets.create()));
        assertTrue(selected.isCurrent(access));

        access.sameThread = false;
        assertFalse(selected.isCurrent(access));
        access.sameThread = true;
        access.serverIdentity = new Object();
        assertFalse(selected.isCurrent(access));
        access.serverIdentity = server;
        access.profileId = UUID.fromString("00000000-0000-0000-0000-000000000018");
        assertFalse(selected.isCurrent(access));
        access.profileId = OWNER;
        access.snapshot = new CompoundTag();
        assertFalse(selected.isCurrent(access));
        assertEquals(
                P4E1SourceFailure.Code.INTEGRATED_OWNER_FRESHNESS_LOST,
                selected.freshnessFailure(access).orElseThrow().code());

        var offThread = new MutableAccess(server, false, OWNER, snapshot);
        assertThrows(IllegalStateException.class, () ->
                P4E1IntegratedSnapshotTraversal.capture(
                        offThread, P4E1TestBudgets.create()));
    }

    @Test
    void relevantRecordCapacityFailureDoesNotPublishAnIntegratedSelection() {
        var budget = P4E1TestBudgets.create();
        assertTrue(budget.checkpointSingle(
                P4E1AuditCounter.RELEVANT_RECORDS,
                P4E1AuditStage.RELEVANT_RECORDS,
                budget.maximum(P4E1AuditCounter.RELEVANT_RECORDS)).isEmpty());

        var failure = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Failure.class,
                P4E1IntegratedSnapshotTraversal.capture(
                        new MutableAccess(new Object(), true, OWNER, new CompoundTag()),
                        budget));
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                failure.failure().code());
        assertEquals(P4E1AuditCounter.RELEVANT_RECORDS,
                failure.failure().counter().orElseThrow());
    }

    @Test
    void sameObjectHostileMutationIsExplicitlyOutsideTheIdentityWitness() {
        var snapshot = new CompoundTag();
        var access = new MutableAccess(new Object(), true, OWNER, snapshot);
        var selected = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(access, P4E1TestBudgets.create()));

        snapshot.putInt("hostile_same_object_mutation", 1);

        // V0 intentionally has no checksum/copy pass: exact object identity cannot see this.
        assertTrue(selected.isCurrent(access));
    }

    @Test
    void allLogicalTagWidthsAndStructuralCountersUseTheSingleBudget() {
        var root = allTagsRoot();
        var budget = P4E1TestBudgets.create();
        var selected = integrated(root, budget);

        var ready = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class,
                selected.traverse(budget));

        assertEquals(156L, ready.logicalEncodedWidth());
        assertEquals(156L, budget.observed(P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE));
        assertEquals(156L, budget.observed(P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL));
        assertEquals(2L, budget.observed(P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE));
        assertEquals(13L, budget.observed(P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE));
        assertEquals(2L, budget.observed(P4E1AuditCounter.LIST_ELEMENTS_PER_FILE));
        assertEquals(2L, budget.observed(P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE));
        assertEquals(2L, budget.observed(P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE));
        assertEquals(2L, budget.observed(P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE));
        assertEquals(30L, budget.observed(P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE));
        assertEquals(10L, budget.observed(P4E1AuditCounter.SCALAR_TAGS_PER_FILE));
        assertEquals(2L, budget.observed(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE));
        assertEquals(0L, budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL));
    }

    @Test
    void compressedCoordinateIsNotApplicableAndTraversalIsOneShot() {
        var budget = P4E1TestBudgets.create();
        var selected = integrated(new CompoundTag(), budget);
        var result = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class,
                selected.traverse(budget));
        assertEquals(4L, result.logicalEncodedWidth());
        assertEquals(0L, budget.observed(P4E1AuditCounter.COMPRESSED_BYTES_TOTAL));
        assertThrows(IllegalStateException.class, () -> selected.traverse(budget));
    }

    @Test
    void nestedAttachmentReferenceAndStandaloneWidthComeFromTheSameTraversal() {
        var attachment = new CompoundTag();
        attachment.putInt("v", 7);
        var attachments = new CompoundTag();
        attachments.put("gramarye:player_skills", attachment);
        var root = new CompoundTag();
        root.put("neoforge:attachments", attachments);
        root.putString("DataVersion", "not-inspected-on-integrated-source");
        var budget = P4E1TestBudgets.create();

        var ready = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class,
                integrated(root, budget).traverse(budget));
        var observation = ready.attachment().orElseThrow();
        assertSame(attachment, observation.tagIdentity());
        assertEquals(10L, observation.exactEncodedWidth());
    }

    @Test
    void wrongTypeOuterAttachmentsIsMissingRatherThanAnInnerParse() {
        var root = new CompoundTag();
        root.putString("neoforge:attachments", "gramarye:player_skills");
        var budget = P4E1TestBudgets.create();

        var ready = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class,
                integrated(root, budget).traverse(budget));

        assertTrue(ready.attachment().isEmpty());
    }

    @Test
    void structuralExactAndPlusOneUseCanonicalCapacityFailure() {
        assertOneUnitBoundary(
                P4E1AuditCounter.COMPOUND_CONTAINERS_PER_FILE,
                new CompoundTag());

        var scalarRoot = new CompoundTag();
        scalarRoot.putByte("", (byte) 0);
        assertOneUnitBoundary(P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE, scalarRoot);
        assertOneUnitBoundary(P4E1AuditCounter.SCALAR_TAGS_PER_FILE, scalarRoot);

        var utfRoot = new CompoundTag();
        utfRoot.putByte("a", (byte) 0);
        assertOneUnitBoundary(P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE, utfRoot);

        var list = new ListTag();
        list.add(ByteTag.ZERO);
        var listRoot = new CompoundTag();
        listRoot.put("", list);
        assertOneUnitBoundary(P4E1AuditCounter.LIST_ELEMENTS_PER_FILE, listRoot);

        var bytesRoot = new CompoundTag();
        bytesRoot.put("", new ByteArrayTag(new byte[1]));
        assertOneUnitBoundary(P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE, bytesRoot);

        var intsRoot = new CompoundTag();
        intsRoot.put("", new IntArrayTag(new int[1]));
        assertOneUnitBoundary(P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE, intsRoot);

        var longsRoot = new CompoundTag();
        longsRoot.put("", new LongArrayTag(new long[1]));
        assertOneUnitBoundary(P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE, longsRoot);
    }

    @Test
    void exactDepth512SucceedsAndDepth513FailsWithoutJavaRecursion() {
        var exactBudget = P4E1TestBudgets.create();
        var exact = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class,
                integrated(compoundChain(512), exactBudget).traverse(exactBudget));
        assertTrue(exact.logicalEncodedWidth() > 4L);
        assertEquals(512L, exactBudget.observed(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE));

        var overBudget = P4E1TestBudgets.create();
        var over = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Failure.class,
                integrated(compoundChain(513), overBudget).traverse(overBudget));
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                over.failure().code());
        assertEquals(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE,
                over.failure().counter().orElseThrow());
        assertEquals(513L, over.failure().observedAtLeast());
        assertEquals(512L, over.failure().maximum());
    }

    @Test
    void integratedProductionSourceHasNoCopyWriterSnbtDfuOrSecondTreePass() throws IOException {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E1IntegratedSnapshotTraversal.java"));
        for (var forbidden : List.of(
                ".copy()",
                "NbtIo.",
                "writeAnyTag",
                "writeUnnamedTag",
                "ByteArrayOutputStream",
                "DataFixTypes",
                "DataVersion",
                "Snbt",
                "SNBT",
                "hashCode()")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertEquals(1, occurrences(source, "while (!work.isEmpty()"));
    }

    private static P4E1IntegratedSnapshotTraversal.Selection.Integrated integrated(
            CompoundTag root, P4E1AuditBudget budget) {
        var access = new MutableAccess(new Object(), true, OWNER, root);
        return assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.Selection.Integrated.class,
                P4E1IntegratedSnapshotTraversal.capture(access, budget));
    }

    private static CompoundTag allTagsRoot() {
        var root = new CompoundTag();
        root.put("b", ByteTag.valueOf((byte) 1));
        root.put("s", ShortTag.valueOf((short) 2));
        root.put("i", IntTag.valueOf(3));
        root.put("l", LongTag.valueOf(4L));
        root.put("f", FloatTag.valueOf(5.0F));
        root.put("d", DoubleTag.valueOf(6.0D));
        root.put("u", StringTag.valueOf("A\0\u0800\ud83d\ude00"));
        root.put("ba", new ByteArrayTag(new byte[] {1, 2}));
        root.put("ia", new IntArrayTag(new int[] {1, 2}));
        root.put("la", new LongArrayTag(new long[] {1L, 2L}));
        var list = new ListTag();
        list.add(IntTag.valueOf(7));
        list.add(IntTag.valueOf(8));
        root.put("li", list);
        var nested = new CompoundTag();
        nested.putString("n", "z");
        root.put("c", nested);
        return root;
    }

    private static CompoundTag compoundChain(int depth) {
        var root = new CompoundTag();
        var cursor = root;
        for (var index = 1; index < depth; index++) {
            var child = new CompoundTag();
            cursor.put("", child);
            cursor = child;
        }
        return root;
    }

    private static void assertOneUnitBoundary(
            P4E1AuditCounter counter, CompoundTag incrementRoot) {
        var exactBudget = P4E1TestBudgets.create();
        var exactScope = exactBudget.newFileScope();
        exactScope.markCompressedBytesNotApplicable();
        var total = aggregate(counter);
        assertTrue(exactScope.checkpointFileAndAggregate(
                counter,
                total,
                stage(counter),
                stage(counter),
                exactBudget.maximum(counter) - 1L).isEmpty());
        var exact = P4E1IntegratedSnapshotTraversal.traverse(incrementRoot, exactScope);
        assertInstanceOf(P4E1IntegratedSnapshotTraversal.TraversalResult.Ready.class, exact);
        assertEquals(exactBudget.maximum(counter), exactBudget.observed(counter));

        var overBudget = P4E1TestBudgets.create();
        var overScope = overBudget.newFileScope();
        overScope.markCompressedBytesNotApplicable();
        assertTrue(overScope.checkpointFileAndAggregate(
                counter,
                total,
                stage(counter),
                stage(counter),
                overBudget.maximum(counter)).isEmpty());
        var over = assertInstanceOf(
                P4E1IntegratedSnapshotTraversal.TraversalResult.Failure.class,
                P4E1IntegratedSnapshotTraversal.traverse(incrementRoot, overScope));
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                over.failure().code());
        assertEquals(counter, over.failure().counter().orElseThrow());
        assertEquals(overBudget.maximum(counter) + 1L, over.failure().observedAtLeast());
    }

    private static P4E1AuditCounter aggregate(P4E1AuditCounter perFile) {
        return switch (perFile) {
            case COMPOUND_CONTAINERS_PER_FILE -> P4E1AuditCounter.COMPOUND_CONTAINERS_TOTAL;
            case COMPOUND_FIELD_ENTRIES_PER_FILE ->
                    P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL;
            case LIST_ELEMENTS_PER_FILE -> P4E1AuditCounter.LIST_ELEMENTS_TOTAL;
            case BYTE_ARRAY_ELEMENTS_PER_FILE -> P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL;
            case INT_ARRAY_ELEMENTS_PER_FILE -> P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL;
            case LONG_ARRAY_ELEMENTS_PER_FILE -> P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL;
            case MODIFIED_UTF8_BYTES_PER_FILE -> P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL;
            case SCALAR_TAGS_PER_FILE -> P4E1AuditCounter.SCALAR_TAGS_TOTAL;
            default -> throw new IllegalArgumentException("not a unit structural counter");
        };
    }

    private static P4E1AuditStage stage(P4E1AuditCounter counter) {
        return switch (counter) {
            case COMPOUND_CONTAINERS_PER_FILE, SCALAR_TAGS_PER_FILE ->
                    P4E1AuditStage.DEPTH_CONTAINER_SCALAR_KIND;
            case COMPOUND_FIELD_ENTRIES_PER_FILE -> P4E1AuditStage.COMPOUND_FIELD_CHECKPOINT;
            case LIST_ELEMENTS_PER_FILE -> P4E1AuditStage.LIST_LENGTH;
            case BYTE_ARRAY_ELEMENTS_PER_FILE,
                    INT_ARRAY_ELEMENTS_PER_FILE,
                    LONG_ARRAY_ELEMENTS_PER_FILE -> P4E1AuditStage.TYPED_ARRAY_LENGTH;
            case MODIFIED_UTF8_BYTES_PER_FILE -> P4E1AuditStage.MODIFIED_UTF_PREFIX;
            default -> throw new IllegalArgumentException("not a unit structural counter");
        };
    }

    private static int occurrences(String source, String needle) {
        var count = 0;
        var index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static Path projectRoot() {
        for (var candidate = Path.of("").toAbsolutePath().normalize();
                candidate != null;
                candidate = candidate.getParent()) {
            if (Files.isRegularFile(candidate.resolve("build.gradle"))
                    && Files.isDirectory(candidate.resolve("src/main/java"))) {
                return candidate;
            }
        }
        throw new AssertionError("Unable to locate the Gradle project root");
    }

    private static final class MutableAccess
            implements P4E1IntegratedSnapshotTraversal.SnapshotAccess {
        private Object serverIdentity;
        private boolean sameThread;
        private UUID profileId;
        private CompoundTag snapshot;

        private MutableAccess(
                Object serverIdentity,
                boolean sameThread,
                UUID profileId,
                CompoundTag snapshot) {
            this.serverIdentity = serverIdentity;
            this.sameThread = sameThread;
            this.profileId = profileId;
            this.snapshot = snapshot;
        }

        @Override
        public Object serverIdentity() {
            return serverIdentity;
        }

        @Override
        public boolean isSameThread() {
            return sameThread;
        }

        @Override
        public UUID profileId() {
            return profileId;
        }

        @Override
        public CompoundTag loadedPlayerSnapshot() {
            return snapshot;
        }
    }
}
