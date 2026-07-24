package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.definition.document.EncodedSkillDocument;
import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;

class P4A2StoreNbtFramingTest {
    @Test
    void exactCeilingsAndFiniteAccounterFormulaAreCanonical() {
        assertEquals(1_114_112, MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES);
        assertEquals(8_388_608, MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES);
        assertEquals(67_108_864, MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES);
        assertEquals(6_488_064,
                StoreNbtFraming.accounterQuota(
                        MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES));
        assertEquals(21_037_056,
                StoreNbtFraming.accounterQuota(
                        MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES));
        assertEquals(138_477_568,
                StoreNbtFraming.accounterQuota(
                        MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES));
    }

    @Test
    void revisionGoldenFramingIsEightyFiveBytesAndRoundTrips() {
        var document = new byte[4_096];
        var envelope = new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(7),
                StorePersistenceSchema.DOCUMENT_ENCODING,
                EncodedSkillDocument.copyOf(document));

        var encoded = StoreNbtFraming.encodeRevision(envelope).successValue().orElseThrow();
        var decoded = StoreNbtFraming.decodeRevision(encoded).successValue().orElseThrow();

        assertEquals(85 + document.length, encoded.byteCount());
        assertEquals(7, decoded.revision().value());
        assertEquals(StorePersistenceSchema.DOCUMENT_ENCODING, decoded.documentEncoding());
        assertArrayEquals(document, decoded.document().copyBytes());
    }

    @Test
    void physicalDtoStringsAreBoundedAndDoNotExposeEncodingOrBytes() {
        var secret = "secret-document-encoding";
        var revision = new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(1),
                secret,
                EncodedSkillDocument.copyOf(new byte[] {11, 22, 33}));

        assertTrue(revision.toString().contains(secret) == false);
        assertTrue(revision.toString().contains("11") == false);
        assertTrue(revision.toString().length() < 128);
    }

    @Test
    void largestCanonicalDocumentFitsAndOuterPlusOneStopsBeforeParse() {
        var document = EncodedSkillDocument.copyOf(
                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES]);
        var encoded = StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(0), StorePersistenceSchema.DOCUMENT_ENCODING, document))
                .successValue().orElseThrow();
        var over = ImmutableRevisionBlob.copyOf(
                new byte[MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES + 1]);

        assertEquals(1_048_661, encoded.byteCount());
        assertTrue(StoreNbtFraming.decodeRevision(encoded).successValue().isPresent());
        assertInstanceOf(
                StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeRevision(over).failureValue().orElseThrow());
    }

    @Test
    void documentCapacityIsDistinctFromOuterRevisionCapacity() throws Exception {
        var root = new CompoundTag();
        root.putInt("revision", 0);
        root.putString("document_encoding", StorePersistenceSchema.DOCUMENT_ENCODING);
        root.putByteArray("document_bytes",
                new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1]);
        var encoded = ImmutableRevisionBlob.copyOf(writeAny(root));

        var failure = assertInstanceOf(
                StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeRevision(encoded).failureValue().orElseThrow());
        assertEquals(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1L,
                failure.observedAtLeast());
    }

    @Test
    void outerExactRevisionReachesDocumentGuardWhileOuterPlusOneStopsFirst() throws Exception {
        var root = new CompoundTag();
        root.putInt("revision", 0);
        root.putString("document_encoding", StorePersistenceSchema.DOCUMENT_ENCODING);
        root.putByteArray("document_bytes", new byte[
                MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES - 85]);
        var exactBytes = writeAny(root);
        var exact = ImmutableRevisionBlob.takeOwnership(exactBytes);
        var over = ImmutableRevisionBlob.takeOwnership(
                new byte[MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES + 1]);

        assertEquals(MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES,
                exact.byteCount());
        assertInstanceOf(StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeRevision(exact).failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.RevisionBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeRevision(over).failureValue().orElseThrow());
    }

    @Test
    void legallyFramedHistoryAndStoreExactBoundsDecodeAndPlusOneStopsFirst() {
        assertLegalExactHistoryRoundTrip();
        assertInstanceOf(StorePersistenceFailure.HistoryBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeHistory(ImmutableHistoryBlob.takeOwnership(
                                new byte[MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES + 1]))
                        .failureValue().orElseThrow());
        assertLegalExactStoreRoundTrip();
        var overFailure = StoreNbtFraming.decodeStore(ImmutableStoreBlob.takeOwnership(
                        new byte[MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES + 1]))
                .failureValue().orElseThrow();
        assertInstanceOf(StorePersistenceFailure.StoreBlobEncodedCapacityExceeded.class,
                overFailure);
    }

    @Test
    void storeHistoryAndUuidPhysicalShapesRoundTripWithoutMapNormalization() {
        var document = EncodedSkillDocument.copyOf(new byte[] {10});
        var revision = StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(3), StorePersistenceSchema.DOCUMENT_ENCODING, document))
                .successValue().orElseThrow();
        var history = new HistoryPersistentEnvelopeV0(
                StoreTestFixtures.skillId(10), StoreTestFixtures.ownerId(11),
                List.of(revision, revision));
        var historyBlob = StoreNbtFraming.encodeHistory(history).successValue().orElseThrow();
        var decodedHistory = StoreNbtFraming.decodeHistory(historyBlob).successValue().orElseThrow();
        var storeBlob = StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(
                0, List.of(historyBlob, historyBlob))).successValue().orElseThrow();
        var decodedStore = StoreNbtFraming.decodeStore(storeBlob).successValue().orElseThrow();

        assertEquals(2, decodedHistory.revisionEntries().size());
        assertEquals(2, decodedStore.historyEntries().size());
        assertEquals(StoreTestFixtures.skillId(10), decodedHistory.skillId());
        assertEquals(StoreTestFixtures.ownerId(11), decodedHistory.owner());
    }

    @Test
    void exactFieldDecoderRejectsDuplicateUnknownWrongTypeAndTrailingBytes() throws Exception {
        var duplicate = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(duplicate)) {
            output.writeByte(10);
            output.writeByte(3);
            output.writeUTF("revision");
            output.writeInt(0);
            output.writeByte(3);
            output.writeUTF("revision");
            output.writeInt(1);
            output.writeByte(8);
            output.writeUTF("document_encoding");
            output.writeUTF(StorePersistenceSchema.DOCUMENT_ENCODING);
            output.writeByte(7);
            output.writeUTF("document_bytes");
            output.writeInt(1);
            output.writeByte(10);
            output.writeByte(0);
        }

        var unknownRoot = new CompoundTag();
        unknownRoot.putInt("store_schema_version", 0);
        unknownRoot.put("history_entries", new ListTag());
        unknownRoot.putInt("unknown", 1);
        var wrongType = new CompoundTag();
        wrongType.putString("store_schema_version", "0");
        wrongType.put("history_entries", new ListTag());
        var valid = StoreNbtFraming.encodeStore(
                new StorePersistentEnvelopeV0(0, List.of())).successValue().orElseThrow();
        var trailing = java.util.Arrays.copyOf(valid.copyBytes(), valid.byteCount() + 1);

        assertInstanceOf(StorePersistenceFailure.MalformedRevisionEnvelope.class,
                StoreNbtFraming.decodeRevision(ImmutableRevisionBlob.copyOf(duplicate.toByteArray()))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(writeAny(unknownRoot)))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(writeAny(wrongType)))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(trailing))
                        .failureValue().orElseThrow());
    }

    @Test
    void missingFieldWrongListElementAndNegativeCountAreMalformedPhysicalShape()
            throws Exception {
        var missing = new CompoundTag();
        missing.putInt("store_schema_version", 0);
        var wrongList = new CompoundTag();
        wrongList.putInt("store_schema_version", 0);
        var ints = new ListTag();
        ints.add(net.minecraft.nbt.IntTag.valueOf(1));
        wrongList.put("history_entries", ints);
        var valid = StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(0, List.of()))
                .successValue().orElseThrow().copyBytes();
        valid[valid.length - 5] = (byte) 0xff;
        valid[valid.length - 4] = (byte) 0xff;
        valid[valid.length - 3] = (byte) 0xff;
        valid[valid.length - 2] = (byte) 0xff;

        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(writeAny(missing)))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(writeAny(wrongList)))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(valid))
                        .failureValue().orElseThrow());
    }

    @Test
    void impossiblePhysicalCountIsMalformedWithoutDomainCapacityClassification() throws Exception {
        var root = new CompoundTag();
        root.putInt("store_schema_version", 0);
        root.put("history_entries", new ListTag());
        var bytes = writeAny(root);
        // Canonical empty Store: list count begins immediately before the final Compound end.
        bytes[bytes.length - 5] = 0x7f;
        bytes[bytes.length - 4] = (byte) 0xff;
        bytes[bytes.length - 3] = (byte) 0xff;
        bytes[bytes.length - 2] = (byte) 0xff;

        var failure = StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(bytes))
                .failureValue().orElseThrow();
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class, failure);
    }

    @Test
    void uuidCodecsUseExactFourIntNbtRepresentation() {
        var history = new HistoryPersistentEnvelopeV0(
                StoreTestFixtures.skillId(1), StoreTestFixtures.ownerId(2), List.of());
        var physical = StoreNbtFraming.encodeHistory(history).successValue().orElseThrow();
        var tag = assertInstanceOf(CompoundTag.class, readAny(physical.copyBytes()));

        assertEquals(4, assertInstanceOf(IntArrayTag.class, tag.get("skill_id")).size());
        assertEquals(4, assertInstanceOf(IntArrayTag.class, tag.get("owner")).size());
    }

    @Test
    void completeOuterPreflightPrecedesNestedMaterialization() throws Exception {
        var lateDuplicate = storeWithListThenTail(
                16_384,
                output -> {
                    writeNamedInt(output, "store_schema_version", 0);
                    writeNamedInt(output, "store_schema_version", 0);
                });
        var lateUnknown = storeWithListThenTail(
                16_384,
                output -> {
                    writeNamedInt(output, "store_schema_version", 0);
                    writeNamedInt(output, "unknown", 1);
                });
        var duplicateCopies = new AtomicInteger();
        var unknownCopies = new AtomicInteger();

        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(
                                ImmutableStoreBlob.copyOf(lateDuplicate),
                                duplicateCopies::incrementAndGet)
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(
                                ImmutableStoreBlob.copyOf(lateUnknown),
                                unknownCopies::incrementAndGet)
                        .failureValue().orElseThrow());
        assertEquals(0, duplicateCopies.get());
        assertEquals(0, unknownCopies.get());
    }

    @Test
    void validReorderedFieldsMaterializeOnlyAfterPreflight() throws Exception {
        var revision = reorderedRevision(
                StorePersistenceSchema.DOCUMENT_ENCODING, new byte[] {42});
        var history = reorderedHistory(revision);
        var store = reorderedStore(history);
        var revisionCopies = new AtomicInteger();
        var historyCopies = new AtomicInteger();
        var storeCopies = new AtomicInteger();

        assertTrue(StoreNbtFraming.decodeRevision(
                        ImmutableRevisionBlob.copyOf(revision),
                        null,
                        revisionCopies::incrementAndGet)
                .successValue().isPresent());
        assertTrue(StoreNbtFraming.decodeHistory(
                        ImmutableHistoryBlob.copyOf(history),
                        historyCopies::incrementAndGet)
                .successValue().isPresent());
        assertTrue(StoreNbtFraming.decodeStore(
                        ImmutableStoreBlob.copyOf(store),
                        storeCopies::incrementAndGet)
                .successValue().isPresent());
        assertEquals(1, revisionCopies.get());
        assertEquals(1, historyCopies.get());
        assertEquals(1, storeCopies.get());
    }

    @Test
    void revisionRoutingChecksUnsupportedEncodingBeforeDocumentCapacity() throws Exception {
        var oversized = new byte[MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1];
        var unsupported = ImmutableRevisionBlob.copyOf(
                reorderedRevision("future_encoding", oversized));
        var supported = ImmutableRevisionBlob.copyOf(
                reorderedRevision(StorePersistenceSchema.DOCUMENT_ENCODING, oversized));
        var unsupportedCopies = new AtomicInteger();
        var supportedCopies = new AtomicInteger();

        assertInstanceOf(StorePersistenceFailure.UnsupportedDocumentEncoding.class,
                StoreNbtFraming.decodeRevision(
                                unsupported,
                                StoreTestFixtures.skillId(91),
                                unsupportedCopies::incrementAndGet)
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.DocumentBlobEncodedCapacityExceeded.class,
                StoreNbtFraming.decodeRevision(
                                supported,
                                StoreTestFixtures.skillId(91),
                                supportedCopies::incrementAndGet)
                        .failureValue().orElseThrow());
        assertEquals(0, unsupportedCopies.get());
        assertEquals(0, supportedCopies.get());
    }

    @Test
    void oversizedDeclaredNestedLengthsThatAreTruncatedAreMalformedBeforeCapacity()
            throws Exception {
        var store = truncatedBlobListEnvelope(
                "store_schema_version",
                "history_entries",
                MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES + 1);
        var history = truncatedHistory(
                MagicSafetyCeilings.MAX_STORE_REVISION_ENTRY_ENCODED_BYTES + 1);
        var revision = truncatedRevision(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES + 1);

        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(ImmutableStoreBlob.copyOf(store))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedHistoryEnvelope.class,
                StoreNbtFraming.decodeHistory(ImmutableHistoryBlob.copyOf(history))
                        .failureValue().orElseThrow());
        assertInstanceOf(StorePersistenceFailure.MalformedRevisionEnvelope.class,
                StoreNbtFraming.decodeRevision(ImmutableRevisionBlob.copyOf(revision))
                        .failureValue().orElseThrow());
    }

    @Test
    void physicalWorkBudgetStopsHugeTinyEntryListBeforeMaterialization() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeByte(9);
            output.writeUTF("history_entries");
            output.writeByte(7);
            output.writeInt(MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES);
            for (var index = 0;
                    index < MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES;
                    index++) {
                output.writeInt(1);
                output.writeByte(0);
            }
            writeNamedInt(output, "store_schema_version", 0);
            output.writeByte(0);
        }
        var copies = new AtomicInteger();

        assertInstanceOf(StorePersistenceFailure.MalformedStoreEnvelope.class,
                StoreNbtFraming.decodeStore(
                                ImmutableStoreBlob.copyOf(bytes.toByteArray()),
                                copies::incrementAndGet)
                        .failureValue().orElseThrow());
        assertEquals(0, copies.get());
    }

    private static byte[] writeAny(CompoundTag tag) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            NbtIo.writeAnyTag(tag, output);
        }
        return bytes.toByteArray();
    }

    private static byte[] storeWithListThenTail(int entryLength, OutputWriter tail)
            throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeByte(9);
            output.writeUTF("history_entries");
            output.writeByte(7);
            output.writeInt(1);
            output.writeInt(entryLength);
            output.write(new byte[entryLength]);
            tail.write(output);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] reorderedRevision(String encoding, byte[] document) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            writeNamedByteArray(output, "document_bytes", document);
            output.writeByte(8);
            output.writeUTF("document_encoding");
            output.writeUTF(encoding);
            writeNamedInt(output, "revision", 0);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] reorderedHistory(byte[] revision) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeByte(9);
            output.writeUTF("revision_entries");
            output.writeByte(7);
            output.writeInt(1);
            output.writeInt(revision.length);
            output.write(revision);
            writeNamedIntArray(output, "owner", new int[] {0, 0, 0, 2});
            writeNamedIntArray(output, "skill_id", new int[] {0, 0, 0, 1});
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] reorderedStore(byte[] history) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            output.writeByte(9);
            output.writeUTF("history_entries");
            output.writeByte(7);
            output.writeInt(1);
            output.writeInt(history.length);
            output.write(history);
            writeNamedInt(output, "store_schema_version", 0);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] truncatedBlobListEnvelope(
            String versionField,
            String listField,
            int declaredNestedLength) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            writeNamedInt(output, versionField, 0);
            output.writeByte(9);
            output.writeUTF(listField);
            output.writeByte(7);
            output.writeInt(1);
            output.writeInt(declaredNestedLength);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] truncatedHistory(int declaredNestedLength) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            writeNamedIntArray(output, "skill_id", new int[] {0, 0, 0, 1});
            writeNamedIntArray(output, "owner", new int[] {0, 0, 0, 2});
            output.writeByte(9);
            output.writeUTF("revision_entries");
            output.writeByte(7);
            output.writeInt(1);
            output.writeInt(declaredNestedLength);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static byte[] truncatedRevision(int declaredDocumentLength) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(10);
            writeNamedInt(output, "revision", 0);
            output.writeByte(8);
            output.writeUTF("document_encoding");
            output.writeUTF(StorePersistenceSchema.DOCUMENT_ENCODING);
            output.writeByte(7);
            output.writeUTF("document_bytes");
            output.writeInt(declaredDocumentLength);
            output.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void writeNamedInt(DataOutputStream output, String name, int value)
            throws Exception {
        output.writeByte(3);
        output.writeUTF(name);
        output.writeInt(value);
    }

    private static void writeNamedByteArray(
            DataOutputStream output, String name, byte[] value) throws Exception {
        output.writeByte(7);
        output.writeUTF(name);
        output.writeInt(value.length);
        output.write(value);
    }

    private static void writeNamedIntArray(
            DataOutputStream output, String name, int[] value) throws Exception {
        output.writeByte(11);
        output.writeUTF(name);
        output.writeInt(value.length);
        for (var element : value) {
            output.writeInt(element);
        }
    }

    @FunctionalInterface
    private interface OutputWriter {
        void write(DataOutputStream output) throws Exception;
    }

    private static net.minecraft.nbt.Tag readAny(byte[] bytes) {
        try {
            return NbtIo.readAnyTag(
                    new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes)),
                    new net.minecraft.nbt.NbtAccounter(10_000, 64));
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertLegalExactHistoryRoundTrip() {
        var encoded = validHistoryAtSize(
                MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES, 71);
        var decoded = StoreNbtFraming.decodeHistory(encoded).successValue().orElseThrow();

        assertEquals(MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES,
                encoded.byteCount());
        for (var revision : decoded.revisionEntries()) {
            assertTrue(StoreNbtFraming.decodeRevision(revision).successValue().isPresent());
        }
    }

    private static void assertLegalExactStoreRoundTrip() {
        var maximumNested = validHistoryAtSize(
                MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES, 72);
        var entries = new java.util.ArrayList<ImmutableHistoryBlob>();
        for (var index = 0; index < 7; index++) {
            entries.add(maximumNested);
        }
        var remainderLength = MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES
                - 52
                - 7 * (Integer.BYTES + MagicSafetyCeilings.MAX_SKILL_HISTORY_ENCODED_BYTES)
                - Integer.BYTES;
        entries.add(validHistoryAtSize(remainderLength, 73));

        var encoded = StoreNbtFraming.encodeStore(new StorePersistentEnvelopeV0(0, entries))
                .successValue().orElseThrow();
        var decoded = StoreNbtFraming.decodeStore(encoded).successValue().orElseThrow();

        assertEquals(MagicSafetyCeilings.MAX_SKILL_STORE_ENCODED_BYTES,
                encoded.byteCount());
        for (var historyBlob : decoded.historyEntries()) {
            var history = StoreNbtFraming.decodeHistory(historyBlob).successValue().orElseThrow();
            for (var revision : history.revisionEntries()) {
                assertTrue(StoreNbtFraming.decodeRevision(revision).successValue().isPresent());
            }
        }
    }

    private static ImmutableHistoryBlob validHistoryAtSize(int encodedLength, long identity) {
        var bodyLength = encodedLength - 85;
        var maximumRevisionLength = 85 + MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES;
        var maximumContribution = Integer.BYTES + maximumRevisionLength;
        var fullEntries = bodyLength / maximumContribution;
        var remaining = bodyLength - fullEntries * maximumContribution;
        var entries = new java.util.ArrayList<ImmutableRevisionBlob>();
        for (var index = 0; index < fullEntries; index++) {
            entries.add(validRevisionAtSize(maximumRevisionLength, index));
        }
        if (remaining > 0) {
            entries.add(validRevisionAtSize(remaining - Integer.BYTES, fullEntries));
        }
        var encoded = StoreNbtFraming.encodeHistory(new HistoryPersistentEnvelopeV0(
                StoreTestFixtures.skillId(identity),
                StoreTestFixtures.ownerId(identity),
                entries)).successValue().orElseThrow();
        assertEquals(encodedLength, encoded.byteCount());
        return encoded;
    }

    private static ImmutableRevisionBlob validRevisionAtSize(
            int encodedLength,
            int revision) {
        var documentLength = encodedLength - 85;
        if (documentLength <= 0
                || documentLength > MagicSafetyCeilings.MAX_SKILL_DOCUMENT_BYTES) {
            throw new AssertionError("requested revision length is not physically representable");
        }
        var encoded = StoreNbtFraming.encodeRevision(new RevisionPersistentEnvelopeV0(
                StoreTestFixtures.revision(revision),
                StorePersistenceSchema.DOCUMENT_ENCODING,
                EncodedSkillDocument.copyOf(new byte[documentLength])))
                .successValue().orElseThrow();
        assertEquals(encodedLength, encoded.byteCount());
        return encoded;
    }
}
