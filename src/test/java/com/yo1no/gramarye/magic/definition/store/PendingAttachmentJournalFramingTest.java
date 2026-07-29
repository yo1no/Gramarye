package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class PendingAttachmentJournalFramingTest {
    @Test
    void zeroSentinelSkipsParserAndPreservesExactHandleIdentity() {
        var source = OpaquePendingAttachmentUpdatesBlob.empty();
        var observer = new CountingObserver();

        var loaded = loaded(PendingAttachmentJournalFraming.loadBytesForTesting(
                new byte[0], observer));

        assertSame(source, loaded.sourcePending());
        assertSame(source, loaded.encoded().pending());
        assertEquals(0, loaded.journal().entryCount());
        assertFalse(loaded.rewriteRequired());
        assertEquals(0, observer.scans);
        assertEquals(0, observer.entries);
    }

    @Test
    void nonZeroEmptyUsesWriteAnyTagCoordinateAndRewritesToZero() throws Exception {
        var root = new CompoundTag();
        root.putInt(PendingAttachmentJournalSchema.VERSION, 0);
        root.put(PendingAttachmentJournalSchema.ENTRIES, new net.minecraft.nbt.ListTag());
        var writeAny = PendingAttachmentJournalTestSupport.writeAny(root);
        var namedBytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(namedBytes)) {
            NbtIo.writeUnnamedTag(root, output);
        }

        var candidate = loaded(PendingAttachmentJournalFraming.load(
                OpaquePendingAttachmentUpdatesBlob.capture(writeAny)));
        var namedFailure = rejected(PendingAttachmentJournalFraming.load(
                OpaquePendingAttachmentUpdatesBlob.capture(namedBytes.toByteArray())));

        assertEquals(Tag.TAG_COMPOUND, Byte.toUnsignedInt(writeAny[0]));
        assertEquals(writeAny.length + 2, namedBytes.size());
        assertTrue(candidate.rewriteRequired());
        assertTrue(candidate.encoded().zero());
        assertEquals(PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                namedFailure.code());
    }

    @Test
    void canonicalSingleEntryRoundTripsWithoutRewriteAndMatchesCodecShapes() {
        var entry = PendingAttachmentJournalTestSupport.physicalEntry(
                1, 2, 0, 1, Optional.empty(), 0);
        var journal = PendingAttachmentJournalTestSupport.journal(entry);
        var encoded = assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();
        var encodedAgain = assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();

        var candidate = loaded(PendingAttachmentJournalFraming.load(encoded.pending()));

        assertArrayEquals(canonicalSingleEntryGolden(entry), encoded.pending().copyBytes());
        assertArrayEquals(encoded.pending().copyBytes(), encodedAgain.pending().copyBytes());
        assertFalse(candidate.rewriteRequired());
        assertEquals(journal.entries(), candidate.journal().entries());
        assertSame(encoded.pending(), candidate.encoded().pending());
        assertTrue(encoded.pending().contentEquals(candidate.encoded().pending()));

        try (var input = new DataInputStream(
                new ByteArrayInputStream(encoded.pending().copyBytes()))) {
            var root = assertInstanceOf(CompoundTag.class, NbtIo.readAnyTag(
                    input, new net.minecraft.nbt.NbtAccounter(1_000_000, 16)));
            assertEquals(-1, input.read());
            var entries = assertInstanceOf(net.minecraft.nbt.ListTag.class,
                    root.get(PendingAttachmentJournalSchema.ENTRIES));
            var encodedEntry = assertInstanceOf(CompoundTag.class, entries.getFirst());
            assertEquals(
                    entry.targetPointer(),
                    com.yo1no.gramarye.magic.definition.document.SkillReference.CODEC
                            .parse(NbtOps.INSTANCE, encodedEntry.get(
                                    PendingAttachmentJournalSchema.TARGET_POINTER))
                            .result().orElseThrow());
            assertEquals(
                    entry.owner(),
                    com.yo1no.gramarye.magic.api.id.SkillOwnerId.CODEC
                            .parse(NbtOps.INSTANCE, encodedEntry.get(
                                    PendingAttachmentJournalSchema.OWNER))
                            .result().orElseThrow());
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @Test
    void primitiveListSecondRootAndEveryTrailingByteAreRejected() throws Exception {
        var primitive = new byte[] {Tag.TAG_INT, 0, 0, 0, 0};
        var list = new byte[] {Tag.TAG_LIST, Tag.TAG_END, 0, 0, 0, 0};
        var canonical = PendingAttachmentJournalTestSupport.rootBytes(List.of(
                PendingAttachmentJournalTestSupport.physicalEntry(
                        1, 2, 0, 1, Optional.empty(), 0)));

        assertEquals(PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                rejected(load(primitive)).code());
        assertEquals(PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                rejected(load(list)).code());
        for (var suffix : List.of(new byte[] {Tag.TAG_COMPOUND, Tag.TAG_END},
                new byte[] {1}, new byte[] {0})) {
            var trailing = Arrays.copyOf(canonical, canonical.length + suffix.length);
            System.arraycopy(suffix, 0, trailing, canonical.length, suffix.length);
            assertEquals(PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                    rejected(load(trailing)).code());
        }
    }

    @Test
    void duplicateIsReportedOnlyAfterLateFramingHasBeenProved() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF(PendingAttachmentJournalSchema.VERSION);
            output.writeInt(0);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF(PendingAttachmentJournalSchema.VERSION);
            output.writeInt(0);
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF("late\u0000field");
            output.writeShort(8);
            output.writeByte('x');
        }

        assertEquals(PendingAttachmentJournalFailure.Code.MALFORMED_ROOT,
                rejected(load(bytes.toByteArray())).code());
    }

    @Test
    void completedRootEntryAndPointerDuplicatesAreRejected() throws Exception {
        for (var location : DuplicateLocation.values()) {
            var failure = rejected(load(journalWithDuplicate(location)));
            assertEquals(PendingAttachmentJournalFailure.Code.DUPLICATE_PHYSICAL_FIELD,
                    failure.code(), location.name());
            assertTrue(failure.observedAtLeast() > 0, location.name());
        }
    }

    @Test
    void outerPlusOneStopsBeforeScannerAnd4097StopsBeforeEntryConstruction()
            throws Exception {
        var over = new byte[
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES + 1];
        var overObserver = new CountingObserver();
        var overFailure = rejected(PendingAttachmentJournalFraming.loadBytesForTesting(
                over, overObserver));

        var bytes = repeatedEntryJournal(
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES + 1, false);
        var countObserver = new CountingObserver();
        var countFailure = rejected(PendingAttachmentJournalFraming.loadBytesForTesting(
                bytes, countObserver));

        assertEquals(PendingAttachmentJournalFailure.Code.ENCODED_CAPACITY_EXCEEDED,
                overFailure.code());
        assertEquals(0, overObserver.scans);

        var exactObserver = new CountingObserver();
        rejected(PendingAttachmentJournalFraming.loadBytesForTesting(
                new byte[MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES],
                exactObserver));
        assertEquals(1, exactObserver.scans);

        assertEquals(PendingAttachmentJournalFailure.Code.ENTRY_COUNT_EXCEEDED,
                countFailure.code());
        assertEquals(1, countObserver.scans);
        assertEquals(0, countObserver.entries);
    }

    @Test
    void currentShapeFailuresPrecedeDeferredRawEntryCount() throws Exception {
        var wrongElement = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(wrongElement)) {
            output.writeByte(Tag.TAG_COMPOUND);
            writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF(PendingAttachmentJournalSchema.ENTRIES);
            output.writeByte(Tag.TAG_BYTE);
            output.writeInt(MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES + 1);
            output.write(new byte[MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES + 1]);
            output.writeByte(Tag.TAG_END);
        }
        var lateUnknownObserver = new CountingObserver();

        var wrongFailure = rejected(load(wrongElement.toByteArray()));
        var unknownFailure = rejected(PendingAttachmentJournalFraming.loadBytesForTesting(
                repeatedEntryJournal(
                        MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES + 1, true),
                lateUnknownObserver));

        assertEquals(PendingAttachmentJournalFailure.Code.WRONG_TAG_TYPE,
                wrongFailure.code());
        assertEquals(PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                unknownFailure.code());
        assertEquals(0, lateUnknownObserver.entries);
    }

    @Test
    void scannerUsesJavaModifiedUtfForArbitraryUnknownFieldNames() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF(PendingAttachmentJournalSchema.VERSION);
            output.writeInt(0);
            output.writeByte(Tag.TAG_BYTE);
            output.writeUTF("nul\u0000and\uD83D\uDE00");
            output.writeByte(1);
            output.writeByte(Tag.TAG_END);
        }

        assertInstanceOf(PendingAttachmentJournalWireScan.Result.Scanned.class,
                PendingAttachmentJournalWireScan.scan(bytes.toByteArray()));
        assertEquals(PendingAttachmentJournalFailure.Code.UNKNOWN_FIELD,
                rejected(load(bytes.toByteArray())).code());
    }

    @Test
    void scannerRejectsMalformedModifiedUtfAndNegativeContainerLengths() throws Exception {
        var malformedUtf = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(malformedUtf)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeByte(Tag.TAG_BYTE);
            output.writeShort(1);
            output.writeByte(0xC2);
            output.writeByte(0);
            output.writeByte(Tag.TAG_END);
        }
        assertWireMalformed(malformedUtf.toByteArray());

        for (var type : List.of(
                Tag.TAG_BYTE_ARRAY, Tag.TAG_LIST, Tag.TAG_INT_ARRAY, Tag.TAG_LONG_ARRAY)) {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeByte(type);
                output.writeUTF("negative_" + type);
                if (type == Tag.TAG_LIST) {
                    output.writeByte(Tag.TAG_BYTE);
                }
                output.writeInt(-1);
                output.writeByte(Tag.TAG_END);
            }
            assertWireMalformed(bytes.toByteArray());
        }
    }

    @Test
    void scannerCoversEveryNbtPayloadTypeAndUsesAnIterativeDeepStack() throws Exception {
        var everyType = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(everyType)) {
            output.writeByte(Tag.TAG_COMPOUND);
            writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
            output.writeByte(Tag.TAG_BYTE);
            output.writeUTF("byte");
            output.writeByte(1);
            output.writeByte(Tag.TAG_SHORT);
            output.writeUTF("short");
            output.writeShort(2);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("int");
            output.writeInt(3);
            output.writeByte(Tag.TAG_LONG);
            output.writeUTF("long");
            output.writeLong(4);
            output.writeByte(Tag.TAG_FLOAT);
            output.writeUTF("float");
            output.writeFloat(5);
            output.writeByte(Tag.TAG_DOUBLE);
            output.writeUTF("double");
            output.writeDouble(6);
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("bytes");
            output.writeInt(1);
            output.writeByte(7);
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF("string");
            output.writeUTF("nul\u0000value");
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF("list");
            output.writeByte(Tag.TAG_BYTE);
            output.writeInt(1);
            output.writeByte(8);
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("compound");
            output.writeByte(Tag.TAG_END);
            output.writeByte(Tag.TAG_INT_ARRAY);
            output.writeUTF("ints");
            output.writeInt(1);
            output.writeInt(9);
            output.writeByte(Tag.TAG_LONG_ARRAY);
            output.writeUTF("longs");
            output.writeInt(1);
            output.writeLong(10);
            output.writeByte(Tag.TAG_END);
        }
        assertInstanceOf(PendingAttachmentJournalWireScan.Result.Scanned.class,
                PendingAttachmentJournalWireScan.scan(everyType.toByteArray()));

        var deep = new ByteArrayOutputStream();
        var depth = 1_024;
        try (var output = new DataOutputStream(deep)) {
            output.writeByte(Tag.TAG_COMPOUND);
            for (var index = 0; index < depth; index++) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("nested");
            }
            for (var index = 0; index <= depth; index++) {
                output.writeByte(Tag.TAG_END);
            }
        }
        assertInstanceOf(PendingAttachmentJournalWireScan.Result.Scanned.class,
                PendingAttachmentJournalWireScan.scan(deep.toByteArray()));
    }

    @Test
    void completedDuplicateWithTrailingDataKeepsFramingPrecedence() throws Exception {
        var duplicate = journalWithDuplicate(DuplicateLocation.ROOT);
        var trailing = Arrays.copyOf(duplicate, duplicate.length + 1);
        trailing[trailing.length - 1] = 1;

        assertEquals(PendingAttachmentJournalFailure.Code.TRAILING_DATA,
                rejected(load(trailing)).code());
    }

    @Test
    void canonicalByteCapacityBoundaryUsesOnlyValidEntries() {
        var maximum = MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES;
        var count = MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_UPDATES;
        var noExpected = encoded(journalWithExpectedPointers(count, 0));
        var oneWithout = encoded(journalWithExpectedPointers(1, 0));
        var oneWith = encoded(journalWithExpectedPointers(1, 1));
        var expectedPointerBytes = oneWith.byteCount() - oneWithout.byteCount();
        var maximumExpectedPointers = Math.min(
                count, (maximum - noExpected.byteCount()) / expectedPointerBytes);

        var boundary = encoded(journalWithExpectedPointers(count, maximumExpectedPointers));

        assertTrue(boundary.byteCount() <= maximum);
        assertTrue(maximum - boundary.byteCount() < expectedPointerBytes);
        if (maximumExpectedPointers < count) {
            var rejected = assertInstanceOf(
                    PendingAttachmentJournalFraming.JournalEncodingResult.Rejected.class,
                    PendingAttachmentJournalFraming.encode(journalWithExpectedPointers(
                            count, maximumExpectedPointers + 1)));
            assertEquals(PendingAttachmentJournalFailure.Code.ENCODED_CAPACITY_EXCEEDED,
                    rejected.failure().code());
        }
    }

    @Test
    void schemaProbeMissingWrongNegativeAndFutureAreDistinctlyBounded() throws Exception {
        var missing = new CompoundTag();
        missing.put(PendingAttachmentJournalSchema.ENTRIES, new net.minecraft.nbt.ListTag());
        var wrong = missing.copy();
        wrong.putLong(PendingAttachmentJournalSchema.VERSION, 0);
        var negative = missing.copy();
        negative.putInt(PendingAttachmentJournalSchema.VERSION, -1);
        var future = missing.copy();
        future.putInt(PendingAttachmentJournalSchema.VERSION, 1);

        assertEquals(PendingAttachmentJournalFailure.Code.MISSING_FIELD,
                rejected(load(PendingAttachmentJournalTestSupport.writeAny(missing))).code());
        assertEquals(PendingAttachmentJournalFailure.Code.WRONG_TAG_TYPE,
                rejected(load(PendingAttachmentJournalTestSupport.writeAny(wrong))).code());
        assertEquals(PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA,
                rejected(load(PendingAttachmentJournalTestSupport.writeAny(negative))).code());
        assertEquals(PendingAttachmentJournalFailure.Code.UNSUPPORTED_SCHEMA,
                rejected(load(PendingAttachmentJournalTestSupport.writeAny(future))).code());
    }

    private static byte[] canonicalSingleEntryGolden(
            PendingAttachmentJournalEntryPhysicalV0 entry) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeByte(Tag.TAG_COMPOUND);
                writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
                output.writeByte(Tag.TAG_LIST);
                output.writeUTF(PendingAttachmentJournalSchema.ENTRIES);
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeInt(1);
                writePhysicalEntry(output, entry);
                output.writeByte(Tag.TAG_END);
            }
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] repeatedEntryJournal(int count, boolean lateUnknown)
            throws Exception {
        var entry = PendingAttachmentJournalTestSupport.physicalEntry(
                1, 2, 0, 1, Optional.empty(), 0);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF(PendingAttachmentJournalSchema.ENTRIES);
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeInt(count);
            for (var index = 0; index < count; index++) {
                writePhysicalEntry(output, entry);
            }
            if (lateUnknown) {
                output.writeByte(Tag.TAG_BYTE);
                output.writeUTF("late_unknown");
                output.writeByte(1);
            }
            output.writeByte(Tag.TAG_END);
        }
        return bytes.toByteArray();
    }

    private static void writePhysicalEntry(
            DataOutputStream output, PendingAttachmentJournalEntryPhysicalV0 entry)
            throws Exception {
        writeUuid(output, PendingAttachmentJournalSchema.OWNER, entry.owner().value());
        writeUuid(output, PendingAttachmentJournalSchema.SKILL_ID, entry.skillId().value());
        writeInt(output, PendingAttachmentJournalSchema.EXPECTED_GENERATION,
                entry.expectedAttachmentGeneration());
        writeInt(output, PendingAttachmentJournalSchema.TARGET_GENERATION,
                entry.targetAttachmentGeneration());
        if (entry.expectedPointer().isPresent()) {
            writeReference(output, PendingAttachmentJournalSchema.EXPECTED_POINTER,
                    entry.expectedPointer().orElseThrow());
        }
        writeReference(output, PendingAttachmentJournalSchema.TARGET_POINTER,
                entry.targetPointer());
        output.writeByte(Tag.TAG_END);
    }

    private static void writeReference(
            DataOutputStream output,
            String name,
            com.yo1no.gramarye.magic.definition.document.SkillReference reference)
            throws Exception {
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeUTF(name);
        output.writeByte(Tag.TAG_STRING);
        output.writeUTF(PendingAttachmentJournalSchema.SKILL_ID);
        output.writeUTF(reference.skillId().value().toString());
        writeInt(output, PendingAttachmentJournalSchema.REVISION,
                reference.revision().value());
        output.writeByte(Tag.TAG_END);
    }

    private static PendingAttachmentJournal journalWithExpectedPointers(
            int count, int expectedPointerCount) {
        var entries = new ArrayList<PendingAttachmentJournalEntryPhysicalV0>(count);
        for (var index = 0; index < count; index++) {
            var skill = PendingAttachmentJournalTestSupport.skill(index + 1L);
            entries.add(new PendingAttachmentJournalEntryPhysicalV0(
                    PendingAttachmentJournalTestSupport.owner(index + 1L),
                    skill,
                    0,
                    1,
                    index < expectedPointerCount
                            ? Optional.of(PendingAttachmentJournalTestSupport.reference(skill, 0))
                            : Optional.empty(),
                    PendingAttachmentJournalTestSupport.reference(skill, 1)));
        }
        return assertInstanceOf(
                PendingAttachmentJournal.DomainAdmission.Admitted.class,
                PendingAttachmentJournal.admitPhysical(
                        new PendingAttachmentJournalPhysicalV0(0, entries))).journal();
    }

    private static EncodedPendingAttachmentJournal encoded(PendingAttachmentJournal journal) {
        return assertInstanceOf(
                PendingAttachmentJournalFraming.JournalEncodingResult.Encoded.class,
                PendingAttachmentJournalFraming.encode(journal)).journal();
    }

    private static void assertWireMalformed(byte[] bytes) {
        var failure = assertInstanceOf(
                PendingAttachmentJournalWireScan.Result.Rejected.class,
                PendingAttachmentJournalWireScan.scan(bytes)).failure();
        assertEquals(PendingAttachmentJournalFailure.Code.MALFORMED_ROOT, failure.code());
    }

    private static PendingAttachmentJournalLoadResult load(byte[] bytes) {
        return PendingAttachmentJournalFraming.load(
                OpaquePendingAttachmentUpdatesBlob.capture(bytes));
    }

    private static byte[] journalWithDuplicate(DuplicateLocation duplicate)
            throws Exception {
        var owner = PendingAttachmentJournalTestSupport.owner(1).value();
        var skill = PendingAttachmentJournalTestSupport.skill(2);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
            if (duplicate == DuplicateLocation.ROOT) {
                writeInt(output, PendingAttachmentJournalSchema.VERSION, 0);
            }
            output.writeByte(Tag.TAG_LIST);
            output.writeUTF(PendingAttachmentJournalSchema.ENTRIES);
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeInt(1);
            writeUuid(output, PendingAttachmentJournalSchema.OWNER, owner);
            if (duplicate == DuplicateLocation.ENTRY) {
                writeUuid(output, PendingAttachmentJournalSchema.OWNER, owner);
            }
            writeUuid(output, PendingAttachmentJournalSchema.SKILL_ID, skill.value());
            writeInt(output, PendingAttachmentJournalSchema.EXPECTED_GENERATION, 0);
            writeInt(output, PendingAttachmentJournalSchema.TARGET_GENERATION, 1);
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF(PendingAttachmentJournalSchema.TARGET_POINTER);
            output.writeByte(Tag.TAG_STRING);
            output.writeUTF(PendingAttachmentJournalSchema.SKILL_ID);
            output.writeUTF(skill.value().toString());
            if (duplicate == DuplicateLocation.POINTER) {
                output.writeByte(Tag.TAG_STRING);
                output.writeUTF(PendingAttachmentJournalSchema.SKILL_ID);
                output.writeUTF(skill.value().toString());
            }
            writeInt(output, PendingAttachmentJournalSchema.REVISION, 0);
            output.writeByte(Tag.TAG_END);
            output.writeByte(Tag.TAG_END);
            output.writeByte(Tag.TAG_END);
        }
        return bytes.toByteArray();
    }

    private static void writeInt(DataOutputStream output, String name, int value)
            throws Exception {
        output.writeByte(Tag.TAG_INT);
        output.writeUTF(name);
        output.writeInt(value);
    }

    private static void writeUuid(
            DataOutputStream output, String name, java.util.UUID value) throws Exception {
        output.writeByte(Tag.TAG_INT_ARRAY);
        output.writeUTF(name);
        output.writeInt(4);
        output.writeInt((int) (value.getMostSignificantBits() >>> 32));
        output.writeInt((int) value.getMostSignificantBits());
        output.writeInt((int) (value.getLeastSignificantBits() >>> 32));
        output.writeInt((int) value.getLeastSignificantBits());
    }

    private static PendingAttachmentJournalLoadCandidate loaded(
            PendingAttachmentJournalLoadResult result) {
        return assertInstanceOf(PendingAttachmentJournalLoadResult.Loaded.class, result)
                .candidate();
    }

    private static PendingAttachmentJournalFailure rejected(
            PendingAttachmentJournalLoadResult result) {
        return assertInstanceOf(PendingAttachmentJournalLoadResult.Rejected.class, result)
                .failure();
    }

    private static final class CountingObserver
            implements PendingAttachmentJournalFraming.LoadObserver {
        private int scans;
        private int entries;

        @Override
        public void scannerInvoked() {
            scans++;
        }

        @Override
        public void entryConstructed() {
            entries++;
        }
    }

    private enum DuplicateLocation {
        ROOT,
        ENTRY,
        POINTER
    }
}
