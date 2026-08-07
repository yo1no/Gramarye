package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class P4E1PlayerDataNbtScannerTest {
    private static final long ATTACHMENT_MAXIMUM = 16_777_216L;

    @Test
    void scansEveryTagKindAndMaterializesOnlySelectedAttachment() throws Exception {
        var selected = new CompoundTag();
        selected.putByte("byte", (byte) 1);
        selected.putShort("short", (short) 2);
        selected.putInt("int", 3);
        selected.putLong("long", 4L);
        selected.putFloat("float", 5.0F);
        selected.putDouble("double", 6.0D);
        selected.putByteArray("bytes", new byte[] {7, 8});
        selected.putString("string", "nul\0-two-\u07ff-three-\u0800-pair-\ud83d\ude03");
        var list = new ListTag();
        list.add(IntTag.valueOf(9));
        list.add(IntTag.valueOf(10));
        selected.put("list", list);
        selected.put("compound", new CompoundTag());
        selected.putIntArray("ints", new int[] {11, 12});
        selected.putLongArray("longs", new long[] {13L, 14L});

        var root = currentRoot(selected);
        root.putByteArray("large_unrelated", new byte[32_768]);
        var encoded = unnamed(root);
        var ready = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scan(encoded));
        var present = assertInstanceOf(
                P4E1PlayerDataNbtScanner.AttachmentObservation.Present.class,
                ready.attachment());

        assertEquals(selected, present.tag());
        assertEquals(writeAnySize(selected), present.exactWriteAnyTagBytes());
        assertArrayEquals(new byte[] {7, 8}, ((CompoundTag) present.tag()).getByteArray("bytes"));
    }

    @Test
    void exactFramingRejectsWrongRootNameTrailingAndPrimitiveRoot() throws Exception {
        var current = currentRoot(null);
        var nonemptyName = bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("named");
            current.write(output);
        });
        var trailing = unnamed(current);
        var withTrailing = java.util.Arrays.copyOf(trailing, trailing.length + 1);
        withTrailing[withTrailing.length - 1] = 0;
        var primitive = bytes(output -> NbtIo.writeUnnamedTag(IntTag.valueOf(3), output));

        assertFailureCode(nonemptyName, P4E1SourceFailure.Code.STRICT_NBT_REJECTED);
        assertFailureCode(withTrailing, P4E1SourceFailure.Code.STRICT_NBT_REJECTED);
        assertFailureCode(primitive, P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN);
    }

    @Test
    void duplicateRawCompoundFieldIsStrictOnly() throws Exception {
        var duplicate = bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("DataVersion");
            output.writeInt(P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("duplicate");
            output.writeInt(1);
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("duplicate");
            output.writeInt(2);
            output.writeByte(Tag.TAG_END);
        });
        var failed = failure(duplicate);
        assertEquals(P4E1SourceFailure.Code.STRICT_NBT_REJECTED, failed.failure().code());
        assertEquals(
                P4E1PlayerDataSourceSelector.FailureCategory.STRICT_ONLY_REJECTION,
                failed.category());
    }

    @Test
    void depth512IsAcceptedAndDepth513StopsAtCapacity() throws Exception {
        assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scan(nestedCompounds(512)));
        var failed = failure(nestedCompounds(513));
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED, failed.failure().code());
        assertEquals(P4E1AuditCounter.CONTAINER_DEPTH_PER_FILE,
                failed.failure().counter().orElseThrow());
        assertEquals(513L, failed.failure().observedAtLeast());
        assertEquals(512L, failed.failure().maximum());
    }

    @Test
    void negativeLengthsAndMalformedModifiedUtfArePlatformFailures() throws Exception {
        var negativeArray = bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("DataVersion");
            output.writeInt(P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
            output.writeByte(Tag.TAG_BYTE_ARRAY);
            output.writeUTF("bad");
            output.writeInt(-1);
        });
        var malformedUtf = bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeShort(1);
            output.writeByte(0xe0);
        });
        assertFailureCode(negativeArray, P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN);
        assertFailureCode(malformedUtf, P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN);
    }

    @Test
    void dataVersionIsExactInt3955AndNeverNormalized() throws Exception {
        assertFailureCode(unnamed(new CompoundTag()), P4E1SourceFailure.Code.DATA_VERSION_MISSING);

        var wrongType = new CompoundTag();
        wrongType.putString("DataVersion", "3955");
        assertFailureCode(unnamed(wrongType), P4E1SourceFailure.Code.DATA_VERSION_WRONG_TYPE);

        for (var value : List.of(-1, 3_954, 3_956)) {
            var root = new CompoundTag();
            root.putInt("DataVersion", value);
            assertFailureCode(unnamed(root), P4E1SourceFailure.Code.DATA_VERSION_NOT_CURRENT);
        }
    }

    @Test
    void attachmentSelectionTreatsPlatformSkippedWrongOuterAsMissing() throws Exception {
        var missing = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scan(unnamed(currentRoot(null))));
        assertEquals(P4E1PlayerDataNbtScanner.AttachmentObservation.Missing.INSTANCE,
                missing.attachment());

        var wrongOuter = currentRoot(null);
        wrongOuter.putString(P4E1PlayerDataNbtScanner.ATTACHMENTS_FIELD, "not-compound");
        var skippedOuter = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scan(unnamed(wrongOuter)));
        assertEquals(
                P4E1PlayerDataNbtScanner.AttachmentObservation.Missing.INSTANCE,
                skippedOuter.attachment());

        var staleWrongOuter = currentRoot(null);
        staleWrongOuter.putInt("DataVersion", P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION - 1);
        staleWrongOuter.putString(
                P4E1PlayerDataNbtScanner.ATTACHMENTS_FIELD, "not-compound");
        assertFailureCode(
                unnamed(staleWrongOuter),
                P4E1SourceFailure.Code.DATA_VERSION_NOT_CURRENT);

        var bytes = new ByteArrayTag(new byte[32]);
        var over = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scan(unnamed(currentRoot(bytes)), 8L));
        var oversize = assertInstanceOf(
                P4E1PlayerDataNbtScanner.AttachmentObservation.Oversize.class,
                over.attachment());
        assertEquals(9L, oversize.observedAtLeast());
        assertEquals(8L, oversize.maximum());
    }

    @Test
    void decompressedCounterMeasuresEveryConsumedByteExactlyOnce() throws Exception {
        var encoded = unnamed(currentRoot(ByteTag.valueOf((byte) 7)));
        var budget = P4E1TestBudgets.create();
        var scope = budget.newFileScope();
        assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded), scope, ATTACHMENT_MAXIMUM));
        assertEquals(encoded.length,
                scope.observed(P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE));
        assertEquals(encoded.length,
                budget.observed(P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL));
    }

    @Test
    void decompressedPerFileAndAggregateAcceptExactHeadroomAndRejectPlusOne()
            throws Exception {
        var encoded = unnamed(currentRoot(ByteTag.valueOf((byte) 7)));
        var perFile = P4E1AuditCounter.DECOMPRESSED_BYTES_PER_FILE;
        var aggregate = P4E1AuditCounter.DECOMPRESSED_BYTES_TOTAL;

        var exactPerFile = scanWithPerFileHeadroom(
                encoded, perFile, aggregate, encoded.length);
        assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                exactPerFile.result());
        assertEquals(
                exactPerFile.scope().maximum(perFile),
                exactPerFile.scope().observed(perFile));

        var overPerFile = scanWithPerFileHeadroom(
                encoded, perFile, aggregate, encoded.length - 1L);
        assertCapacityFailure(overPerFile.result(), perFile);

        var exactAggregateBudget = P4E1TestBudgets.create();
        fillAggregateTo(
                exactAggregateBudget,
                perFile,
                aggregate,
                exactAggregateBudget.maximum(aggregate) - encoded.length);
        var exactAggregateScope = exactAggregateBudget.newFileScope();
        assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded),
                        exactAggregateScope,
                        ATTACHMENT_MAXIMUM));
        assertEquals(
                exactAggregateBudget.maximum(aggregate),
                exactAggregateBudget.observed(aggregate));

        var overAggregateBudget = P4E1TestBudgets.create();
        fillAggregateTo(
                overAggregateBudget,
                perFile,
                aggregate,
                overAggregateBudget.maximum(aggregate) - encoded.length + 1L);
        var overAggregateScope = overAggregateBudget.newFileScope();
        assertCapacityFailure(
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded),
                        overAggregateScope,
                        ATTACHMENT_MAXIMUM),
                aggregate);
        assertEquals(
                overAggregateBudget.maximum(aggregate),
                overAggregateBudget.observed(aggregate));
        assertEquals(encoded.length - 1L, overAggregateScope.observed(perFile));
    }

    @Test
    void listAndTypedArrayCountersAcceptExactHeadroomAndRejectPlusOneBeforePayload()
            throws Exception {
        assertExactAndPlusOne(
                P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("value");
                    output.writeByte(Tag.TAG_INT);
                    output.writeInt(2);
                    output.writeInt(1);
                    output.writeInt(2);
                }),
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_LIST);
                    output.writeUTF("value");
                    output.writeByte(Tag.TAG_INT);
                    output.writeInt(3);
                }),
                2L);
        assertExactAndPlusOne(
                P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE,
                P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_TOTAL,
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(2);
                    output.write(new byte[] {1, 2});
                }),
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_BYTE_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(3);
                }),
                2L);
        assertExactAndPlusOne(
                P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE,
                P4E1AuditCounter.INT_ARRAY_ELEMENTS_TOTAL,
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_INT_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(2);
                    output.writeInt(1);
                    output.writeInt(2);
                }),
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_INT_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(3);
                }),
                2L);
        assertExactAndPlusOne(
                P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE,
                P4E1AuditCounter.LONG_ARRAY_ELEMENTS_TOTAL,
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_LONG_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(2);
                    output.writeLong(1L);
                    output.writeLong(2L);
                }),
                rawCurrentRoot(output -> {
                    output.writeByte(Tag.TAG_LONG_ARRAY);
                    output.writeUTF("value");
                    output.writeInt(3);
                }),
                2L);
    }

    @Test
    void compoundAndScalarCheckpointsAcceptExactHeadroomAndPrecedeNameOrPayloadReads()
            throws Exception {
        var onlyDataVersion = rawCurrentRoot(output -> { });
        var compoundExact = scanWithPerFileHeadroom(
                onlyDataVersion,
                P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL,
                1L);
        assertInstanceOf(P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                compoundExact.result());
        assertEquals(
                compoundExact.scope().maximum(
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE),
                compoundExact.scope().observed(
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE));

        var fieldNameNotPresent = rawCurrentRoot(output -> output.writeByte(Tag.TAG_INT));
        assertCapacityFailure(
                scanWithPerFileHeadroom(
                        fieldNameNotPresent,
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE,
                        P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_TOTAL,
                        1L).result(),
                P4E1AuditCounter.COMPOUND_FIELD_ENTRIES_PER_FILE);

        var scalarExact = scanWithPerFileHeadroom(
                onlyDataVersion,
                P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                1L);
        assertInstanceOf(P4E1PlayerDataNbtScanner.ScanResult.Ready.class,
                scalarExact.result());

        var scalarPayloadNotPresent = rawCurrentRoot(output -> {
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("second");
        });
        assertCapacityFailure(
                scanWithPerFileHeadroom(
                        scalarPayloadNotPresent,
                        P4E1AuditCounter.SCALAR_TAGS_PER_FILE,
                        P4E1AuditCounter.SCALAR_TAGS_TOTAL,
                        1L).result(),
                P4E1AuditCounter.SCALAR_TAGS_PER_FILE);
    }

    @Test
    void modifiedUtfCoversNullTwoByteThreeByteAndSurrogateUnitsWithExactAccounting()
            throws Exception {
        var value = "ascii-\0-\u07ff-\u0800-\ud83d\ude03";
        var root = currentRoot(null);
        root.putString("m", value);
        var encoded = unnamed(root);
        var expectedUtfBytes = modifiedUtfBytes("")
                + modifiedUtfBytes("DataVersion")
                + modifiedUtfBytes("m")
                + modifiedUtfBytes(value);
        var scanned = scanWithPerFileHeadroom(
                encoded,
                P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE,
                P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL,
                expectedUtfBytes);

        assertInstanceOf(P4E1PlayerDataNbtScanner.ScanResult.Ready.class, scanned.result());
        assertEquals(
                scanned.scope().maximum(P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE),
                scanned.scope().observed(P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE));

        var nameBytesNotPresent = rawCurrentRoot(output -> {
            output.writeByte(Tag.TAG_INT);
            output.writeShort(1);
        });
        assertCapacityFailure(
                scanWithPerFileHeadroom(
                        nameBytesNotPresent,
                        P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE,
                        P4E1AuditCounter.MODIFIED_UTF8_BYTES_TOTAL,
                        modifiedUtfBytes("DataVersion")).result(),
                P4E1AuditCounter.MODIFIED_UTF8_BYTES_PER_FILE);
    }

    @Test
    void malformedAndTruncatedModifiedUtfArePlatformFailures() throws Exception {
        for (var malformed : List.of(
                rawRootName(1, 0xc2),
                rawRootName(2, 0xc2, 0x41),
                rawRootName(2, 0xe0, 0x80),
                rawRootName(3, 0xe0, 0x80, 0x41),
                rawRootName(1, 0xf0),
                rawRootName(2, 0x41))) {
            assertFailureCode(malformed, P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN);
        }
    }

    @Test
    void everyLengthKindRejectsNegativeAndHugeDeclarationsBeforePayloadAllocation()
            throws Exception {
        for (var kind : List.of(
                LengthKind.LIST,
                LengthKind.BYTE_ARRAY,
                LengthKind.INT_ARRAY,
                LengthKind.LONG_ARRAY)) {
            assertFailureCode(
                    rawLength(kind, -1),
                    P4E1SourceFailure.Code.PLATFORM_READ_FAILURE_PROVEN);
            assertCapacityFailure(
                    scan(rawLength(kind, Integer.MAX_VALUE)),
                    kind.counter());
        }
    }

    @Test
    void scannerPairCheckpointUsesPerFilePrecedenceAndDoesNotPartiallyPublishAggregateFailure()
            throws Exception {
        var encoded = rawLength(LengthKind.LIST, 2);

        var perFileFirst = P4E1TestBudgets.create();
        var perFileScope = perFileFirst.newFileScope();
        primePerFile(
                perFileScope,
                P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                1L);
        fillAggregateTo(
                perFileFirst,
                P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                perFileFirst.maximum(P4E1AuditCounter.LIST_ELEMENTS_TOTAL) - 1L);
        var aggregateBefore = perFileFirst.observed(P4E1AuditCounter.LIST_ELEMENTS_TOTAL);
        assertCapacityFailure(
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded), perFileScope, ATTACHMENT_MAXIMUM),
                P4E1AuditCounter.LIST_ELEMENTS_PER_FILE);
        assertEquals(aggregateBefore,
                perFileFirst.observed(P4E1AuditCounter.LIST_ELEMENTS_TOTAL));

        var aggregateOnly = P4E1TestBudgets.create();
        fillAggregateTo(
                aggregateOnly,
                P4E1AuditCounter.LIST_ELEMENTS_PER_FILE,
                P4E1AuditCounter.LIST_ELEMENTS_TOTAL,
                aggregateOnly.maximum(P4E1AuditCounter.LIST_ELEMENTS_TOTAL) - 1L);
        var aggregateScope = aggregateOnly.newFileScope();
        assertCapacityFailure(
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded), aggregateScope, ATTACHMENT_MAXIMUM),
                P4E1AuditCounter.LIST_ELEMENTS_TOTAL);
        assertEquals(0L,
                aggregateScope.observed(P4E1AuditCounter.LIST_ELEMENTS_PER_FILE));
        assertEquals(
                aggregateOnly.maximum(P4E1AuditCounter.LIST_ELEMENTS_TOTAL) - 1L,
                aggregateOnly.observed(P4E1AuditCounter.LIST_ELEMENTS_TOTAL));
    }

    @Test
    void productionScannerDoesNotDelegateToNbtIoOrDataFixers() throws Exception {
        var source = Files.readString(projectRoot().resolve(
                "src/main/java/com/yo1no/gramarye/magic/definition/store/"
                        + "P4E1PlayerDataNbtScanner.java"));
        for (var forbidden : List.of(
                "NbtIo",
                "NbtAccounter",
                "DataFixer",
                "DataFixTypes",
                "updateToCurrentVersion",
                "unlimitedHeap")) {
            assertFalse(source.contains(forbidden), forbidden);
        }
        assertTrue(source.contains("Math.addExact(left, right)"));
        assertTrue(source.contains("Math.multiplyExact((long) value, (long) width)"));
    }

    private static P4E1PlayerDataNbtScanner.ScanResult scan(byte[] encoded) throws Exception {
        return scan(encoded, ATTACHMENT_MAXIMUM);
    }

    private static P4E1PlayerDataNbtScanner.ScanResult scan(
            byte[] encoded, long attachmentMaximum) throws Exception {
        var budget = P4E1TestBudgets.create();
        return P4E1PlayerDataNbtScanner.scan(
                new ByteArrayInputStream(encoded), budget.newFileScope(), attachmentMaximum);
    }

    private static P4E1PlayerDataNbtScanner.ScanResult.Failure failure(byte[] encoded)
            throws Exception {
        return assertInstanceOf(P4E1PlayerDataNbtScanner.ScanResult.Failure.class, scan(encoded));
    }

    private static void assertFailureCode(byte[] encoded, P4E1SourceFailure.Code expected)
            throws Exception {
        assertEquals(expected, failure(encoded).failure().code());
    }

    private static void assertExactAndPlusOne(
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate,
            byte[] exact,
            byte[] plusOne,
            long exactDelta) throws Exception {
        var accepted = scanWithPerFileHeadroom(exact, perFile, aggregate, exactDelta);
        assertInstanceOf(P4E1PlayerDataNbtScanner.ScanResult.Ready.class, accepted.result());
        assertEquals(accepted.scope().maximum(perFile), accepted.scope().observed(perFile));

        var rejected = scanWithPerFileHeadroom(plusOne, perFile, aggregate, exactDelta);
        assertCapacityFailure(rejected.result(), perFile);
        assertEquals(rejected.scope().maximum(perFile) - exactDelta,
                rejected.scope().observed(perFile));
    }

    private static ScopedScan scanWithPerFileHeadroom(
            byte[] encoded,
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate,
            long remaining) throws Exception {
        var budget = P4E1TestBudgets.create();
        var scope = budget.newFileScope();
        primePerFile(scope, perFile, aggregate, remaining);
        return new ScopedScan(
                P4E1PlayerDataNbtScanner.scan(
                        new ByteArrayInputStream(encoded), scope, ATTACHMENT_MAXIMUM),
                budget,
                scope);
    }

    private static void primePerFile(
            P4E1AuditBudget.FileScope scope,
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate,
            long remaining) {
        var maximum = scope.maximum(perFile);
        assertTrue(remaining >= 0L && remaining <= maximum);
        assertTrue(scope.checkpointFileAndAggregate(
                perFile,
                aggregate,
                P4E1AuditStage.TYPED_ARRAY_LENGTH,
                P4E1AuditStage.TYPED_ARRAY_LENGTH,
                maximum - remaining).isEmpty());
    }

    private static void fillAggregateTo(
            P4E1AuditBudget budget,
            P4E1AuditCounter perFile,
            P4E1AuditCounter aggregate,
            long target) {
        var remaining = target - budget.observed(aggregate);
        assertTrue(remaining >= 0L);
        while (remaining > 0L) {
            var scope = budget.newFileScope();
            var delta = Math.min(remaining, budget.maximum(perFile));
            assertTrue(scope.checkpointFileAndAggregate(
                    perFile,
                    aggregate,
                    P4E1AuditStage.TYPED_ARRAY_LENGTH,
                    P4E1AuditStage.TYPED_ARRAY_LENGTH,
                    delta).isEmpty());
            remaining -= delta;
        }
    }

    private static void assertCapacityFailure(
            P4E1PlayerDataNbtScanner.ScanResult result,
            P4E1AuditCounter counter) {
        var failure = assertInstanceOf(
                P4E1PlayerDataNbtScanner.ScanResult.Failure.class, result);
        assertEquals(P4E1SourceFailure.Code.COUNTER_CAPACITY_EXCEEDED,
                failure.failure().code());
        assertEquals(counter, failure.failure().counter().orElseThrow());
        assertEquals(failure.failure().maximum() + 1L,
                failure.failure().observedAtLeast());
    }

    private static CompoundTag currentRoot(Tag selected) {
        var root = new CompoundTag();
        root.putInt("DataVersion", P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
        if (selected != null) {
            var attachments = new CompoundTag();
            attachments.put(P4E1PlayerDataNbtScanner.PLAYER_SKILLS_FIELD, selected);
            root.put(P4E1PlayerDataNbtScanner.ATTACHMENTS_FIELD, attachments);
        }
        return root;
    }

    private static byte[] rawCurrentRoot(Writer fields) throws Exception {
        return bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("DataVersion");
            output.writeInt(P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
            fields.write(output);
            output.writeByte(Tag.TAG_END);
        });
    }

    private static byte[] rawLength(LengthKind kind, int length) throws Exception {
        return rawCurrentRoot(output -> {
            output.writeByte(kind.tagType());
            output.writeUTF("value");
            if (kind == LengthKind.LIST) {
                output.writeByte(Tag.TAG_BYTE);
            }
            output.writeInt(length);
        });
    }

    private static byte[] rawRootName(int declaredLength, int... encodedBytes) throws Exception {
        return bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeShort(declaredLength);
            for (var encodedByte : encodedBytes) {
                output.writeByte(encodedByte);
            }
        });
    }

    private static int modifiedUtfBytes(String value) throws Exception {
        return bytes(output -> output.writeUTF(value)).length - Short.BYTES;
    }

    private static Path projectRoot() {
        var current = Path.of("").toAbsolutePath().normalize();
        while (current != null && !Files.isRegularFile(current.resolve("settings.gradle"))) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IllegalStateException("project root unavailable");
        }
        return current;
    }

    private static byte[] nestedCompounds(int depth) throws Exception {
        return bytes(output -> {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("DataVersion");
            output.writeInt(P4E1PlayerDataNbtScanner.CURRENT_DATA_VERSION);
            for (var index = 1; index < depth; index++) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("nested");
            }
            for (var index = 0; index < depth; index++) {
                output.writeByte(Tag.TAG_END);
            }
        });
    }

    private static byte[] unnamed(Tag tag) throws Exception {
        return bytes(output -> NbtIo.writeUnnamedTag(tag, output));
    }

    private static long writeAnySize(Tag tag) throws Exception {
        return bytes(output -> NbtIo.writeAnyTag(tag, output)).length;
    }

    private static byte[] bytes(Writer writer) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            writer.write(output);
        }
        return bytes.toByteArray();
    }

    @FunctionalInterface
    private interface Writer {
        void write(DataOutputStream output) throws Exception;
    }

    private record ScopedScan(
            P4E1PlayerDataNbtScanner.ScanResult result,
            P4E1AuditBudget budget,
            P4E1AuditBudget.FileScope scope) {
    }

    private enum LengthKind {
        LIST(Tag.TAG_LIST, P4E1AuditCounter.LIST_ELEMENTS_PER_FILE),
        BYTE_ARRAY(Tag.TAG_BYTE_ARRAY, P4E1AuditCounter.BYTE_ARRAY_ELEMENTS_PER_FILE),
        INT_ARRAY(Tag.TAG_INT_ARRAY, P4E1AuditCounter.INT_ARRAY_ELEMENTS_PER_FILE),
        LONG_ARRAY(Tag.TAG_LONG_ARRAY, P4E1AuditCounter.LONG_ARRAY_ELEMENTS_PER_FILE);

        private final int tagType;
        private final P4E1AuditCounter counter;

        LengthKind(int tagType, P4E1AuditCounter counter) {
            this.tagType = tagType;
            this.counter = counter;
        }

        private int tagType() {
            return tagType;
        }

        private P4E1AuditCounter counter() {
            return counter;
        }
    }
}
