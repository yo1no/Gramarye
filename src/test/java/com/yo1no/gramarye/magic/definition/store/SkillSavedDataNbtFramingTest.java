package com.yo1no.gramarye.magic.definition.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class SkillSavedDataNbtFramingTest {
    @Test
    void canonicalRootParsesBothOpaqueBlobsAndGoldenFramingIsPlusTwentySix() {
        var store = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        var pending = new byte[] {3, 1, 4, 1, 5};
        var inner = SkillSavedDataTestSupport.standaloneInner(store, pending);
        var whole = SkillSavedDataTestSupport.canonicalWholeRoot(store, pending);

        var parsed = success(whole);

        assertEquals(91, SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES);
        assertArrayEquals(new byte[] {Tag.TAG_COMPOUND, 0, 0},
                Arrays.copyOf(inner, 3));
        assertArrayEquals(new byte[] {Tag.TAG_COMPOUND, 0, 0},
                Arrays.copyOf(whole, 3));
        assertEquals(
                SkillSavedDataPersistenceSchema.WHOLE_ROOT_V0_FRAMING_OVERHEAD,
                whole.length - inner.length);
        assertEquals(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES
                        + store.length + pending.length,
                parsed.innerEncodedByteCount());
        assertArrayEquals(store, parsed.storeBlob().copyBytes());
        assertArrayEquals(pending, parsed.pending().copyBytes());
    }

    @Test
    void fieldOrderIsIrrelevantButBothExactFieldSetsAreStrict() throws Exception {
        var store = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        var pending = new byte[] {9, 8, 7};
        var reordered = customRoot(store, pending, Mutation.REORDER_BOTH);

        assertArrayEquals(store, success(reordered).storeBlob().copyBytes());

        for (var mutation : Arrays.stream(Mutation.values())
                .filter(candidate -> candidate != Mutation.NONE)
                .filter(candidate -> candidate != Mutation.REORDER_BOTH)
                .toList()) {
            assertInstanceOf(
                    SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope.class,
                    failure(customRoot(store, pending, mutation)));
        }
    }

    @Test
    void nonEmptyNamedRootAndEveryTrailingByteAreRejected() {
        var canonical = SkillSavedDataTestSupport.canonicalWholeRoot(
                SkillSavedDataTestSupport.canonicalEmptyStoreBlob(), new byte[0]);
        var named = new byte[canonical.length + 1];
        named[0] = canonical[0];
        named[1] = 0;
        named[2] = 1;
        named[3] = 'x';
        System.arraycopy(canonical, 3, named, 4, canonical.length - 3);
        var trailing = Arrays.copyOf(canonical, canonical.length + 1);
        trailing[trailing.length - 1] = 0;

        assertInstanceOf(
                SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope.class,
                failure(named));
        assertInstanceOf(
                SkillSavedDataCarrierFailure.MalformedSavedDataEnvelope.class,
                failure(trailing));
    }

    @Test
    void pendingPayloadAcceptsExactCeilingAndRejectsPlusOneAtItsOwnLayer() {
        var store = SkillSavedDataTestSupport.canonicalEmptyStoreBlob();
        var exact = SkillSavedDataTestSupport.canonicalWholeRootWithZeroPayloads(
                0,
                store.length,
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES);
        System.arraycopy(store, 0, exact, storePayloadOffset(exact), store.length);
        var plusOne = SkillSavedDataTestSupport.canonicalWholeRootWithZeroPayloads(
                0,
                store.length,
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES + 1);
        System.arraycopy(store, 0, plusOne, storePayloadOffset(plusOne), store.length);

        assertEquals(
                MagicSafetyCeilings.MAX_PENDING_ATTACHMENT_JOURNAL_ENCODED_BYTES,
                success(exact).pending().byteCount());
        assertInstanceOf(
                SkillSavedDataCarrierFailure.PendingAttachmentUpdatesCapacityExceeded.class,
                failure(plusOne));
    }

    @Test
    void exactInnerFixturePassesFiniteQuotaAndPlusOneFailsAtCarrierLayer()
            throws Exception {
        var exactStoreLength = MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES
                - SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES;
        var exact = SkillSavedDataTestSupport.canonicalWholeRootWithZeroPayloads(
                0, exactStoreLength, 0);
        assertEquals(
                SkillSavedDataPersistenceSchema.MAX_WHOLE_DECOMPRESSED_ROOT_BYTES,
                exact.length);

        var finite = new StrictNbtFramingInput(
                exact, SkillSavedDataPersistenceSchema.FINITE_WHOLE_ROOT_NBT_QUOTA);
        finite.verifyFiniteMaterializationQuota();
        assertInstanceOf(
                SkillSavedDataCarrierFailure.StoreLoadFailed.class,
                failure(exact));

        var plusOne = SkillSavedDataTestSupport.canonicalWholeRootWithZeroPayloads(
                0, exactStoreLength + 1, 0);
        assertInstanceOf(
                SkillSavedDataCarrierFailure.DecompressedWholeRootCapacityExceeded.class,
                failure(plusOne));
    }

    @Test
    void standaloneInnerCoordinateAcceptsExactAndRejectsPlusOneAtItsOwnLayer() {
        var exactStoreLength = MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES
                - SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES;
        var exactWhole = SkillSavedDataTestSupport.canonicalWholeRootWithZeroPayloads(
                0, exactStoreLength, 0);
        var innerOffset = 3 + 1 + 2
                + SkillSavedDataPersistenceSchema.DATA_FIELD.length();
        var exactInner = new byte[
                MagicSafetyCeilings.MAX_SKILL_SAVED_DATA_CARRIER_ENCODED_BYTES];
        exactInner[0] = Tag.TAG_COMPOUND;
        exactInner[1] = 0;
        exactInner[2] = 0;
        System.arraycopy(
                exactWhole,
                innerOffset,
                exactInner,
                3,
                exactInner.length - 3);
        var plusOne = Arrays.copyOf(exactInner, exactInner.length + 1);

        assertInstanceOf(
                SkillSavedDataCarrierFailure.StoreLoadFailed.class,
                SkillSavedDataNbtFraming.decodeInnerCarrier(exactInner)
                        .failureValue().orElseThrow());
        assertInstanceOf(
                SkillSavedDataCarrierFailure.SavedDataCarrierCapacityExceeded.class,
                SkillSavedDataNbtFraming.decodeInnerCarrier(plusOne)
                        .failureValue().orElseThrow());
    }

    @Test
    void zeroLengthStoreIsReportedAsNestedStoreFailure() {
        var root = SkillSavedDataTestSupport.canonicalWholeRoot(new byte[0], new byte[0]);
        var nested = assertInstanceOf(
                SkillSavedDataCarrierFailure.StoreLoadFailed.class,
                failure(root));
        assertInstanceOf(
                StorePersistenceFailure.MalformedStoreEnvelope.class,
                nested.failure());
    }

    private static SkillSavedDataNbtFraming.ParsedSavedDataEnvelope success(byte[] bytes) {
        return SkillSavedDataNbtFraming.decodeWholeRoot(new ByteArrayInputStream(bytes))
                .successValue().orElseThrow();
    }

    private static SkillSavedDataCarrierFailure failure(byte[] bytes) {
        return SkillSavedDataNbtFraming.decodeWholeRoot(new ByteArrayInputStream(bytes))
                .failureValue().orElseThrow();
    }

    private static int storePayloadOffset(byte[] root) {
        var field = SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (var index = 0; index <= root.length - field.length - 4; index++) {
            var matches = true;
            for (var offset = 0; offset < field.length; offset++) {
                matches &= root[index + offset] == field[offset];
            }
            if (matches) {
                return index + field.length + Integer.BYTES;
            }
        }
        throw new AssertionError("store field not found");
    }

    private static byte[] customRoot(byte[] store, byte[] pending, Mutation mutation)
            throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) {
            output.writeByte(Tag.TAG_COMPOUND);
            output.writeUTF("");
            if (mutation == Mutation.REORDER_BOTH) {
                writeDataVersion(output, Tag.TAG_INT);
            }
            if (mutation != Mutation.MISSING_DATA) {
                output.writeByte(mutation == Mutation.WRONG_DATA_TYPE
                        ? Tag.TAG_BYTE_ARRAY : Tag.TAG_COMPOUND);
                output.writeUTF(SkillSavedDataPersistenceSchema.DATA_FIELD);
                if (mutation == Mutation.WRONG_DATA_TYPE) {
                    output.writeInt(0);
                } else {
                    writeInner(output, store, pending, mutation);
                }
            }
            if (mutation == Mutation.DUPLICATE_DATA) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF(SkillSavedDataPersistenceSchema.DATA_FIELD);
                writeInner(output, store, pending, Mutation.NONE);
            }
            if (mutation == Mutation.DUPLICATE_DATA_VERSION) {
                writeDataVersion(output, Tag.TAG_INT);
            }
            if (mutation == Mutation.UNKNOWN_OUTER) {
                output.writeByte(Tag.TAG_INT);
                output.writeUTF("unknown");
                output.writeInt(1);
            }
            if (mutation != Mutation.MISSING_DATA_VERSION
                    && mutation != Mutation.REORDER_BOTH) {
                writeDataVersion(output, mutation == Mutation.WRONG_DATA_VERSION_TYPE
                        ? Tag.TAG_LONG : Tag.TAG_INT);
            }
            output.writeByte(Tag.TAG_END);
        }
        return bytes.toByteArray();
    }

    private static void writeInner(
            DataOutputStream output,
            byte[] store,
            byte[] pending,
            Mutation mutation) throws IOException {
        if (mutation == Mutation.REORDER_BOTH) {
            writeByteArray(output,
                    SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD, pending);
            writeByteArray(output, SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, store);
            writeSchema(output, Tag.TAG_INT);
        } else {
            if (mutation != Mutation.MISSING_SCHEMA) {
                writeSchema(output, mutation == Mutation.WRONG_SCHEMA_TYPE
                        ? Tag.TAG_LONG : Tag.TAG_INT);
            }
            if (mutation != Mutation.MISSING_STORE) {
                if (mutation == Mutation.WRONG_STORE_TYPE) {
                    output.writeByte(Tag.TAG_INT);
                    output.writeUTF(SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD);
                    output.writeInt(1);
                } else {
                    writeByteArray(output, SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, store);
                }
            }
            if (mutation != Mutation.MISSING_PENDING
                    && mutation != Mutation.WRONG_PENDING_TYPE) {
                writeByteArray(output,
                        SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD, pending);
            }
        }
        if (mutation == Mutation.DUPLICATE_STORE) {
            writeByteArray(output, SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD, store);
        }
        if (mutation == Mutation.DUPLICATE_SCHEMA) {
            writeSchema(output, Tag.TAG_INT);
        }
        if (mutation == Mutation.DUPLICATE_PENDING) {
            writeByteArray(output,
                    SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD, pending);
        }
        if (mutation == Mutation.WRONG_PENDING_TYPE) {
            output.writeByte(Tag.TAG_INT);
            output.writeUTF(SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD);
            output.writeInt(1);
        }
        if (mutation == Mutation.UNKNOWN_INNER) {
            output.writeByte(Tag.TAG_INT);
            output.writeUTF("unknown");
            output.writeInt(1);
        }
        output.writeByte(Tag.TAG_END);
    }

    private static void writeSchema(DataOutputStream output, int type) throws IOException {
        output.writeByte(type);
        output.writeUTF(SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD);
        if (type == Tag.TAG_LONG) {
            output.writeLong(0);
        } else {
            output.writeInt(0);
        }
    }

    private static void writeDataVersion(DataOutputStream output, int type) throws IOException {
        output.writeByte(type);
        output.writeUTF(SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD);
        if (type == Tag.TAG_LONG) {
            output.writeLong(3_958);
        } else {
            output.writeInt(3_958);
        }
    }

    private static void writeByteArray(
            DataOutputStream output,
            String name,
            byte[] value) throws IOException {
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeUTF(name);
        output.writeInt(value.length);
        output.write(value);
    }

    private enum Mutation {
        NONE,
        REORDER_BOTH,
        DUPLICATE_DATA,
        DUPLICATE_DATA_VERSION,
        UNKNOWN_OUTER,
        MISSING_DATA,
        WRONG_DATA_TYPE,
        MISSING_DATA_VERSION,
        WRONG_DATA_VERSION_TYPE,
        DUPLICATE_STORE,
        DUPLICATE_SCHEMA,
        DUPLICATE_PENDING,
        UNKNOWN_INNER,
        MISSING_SCHEMA,
        WRONG_SCHEMA_TYPE,
        MISSING_STORE,
        WRONG_STORE_TYPE,
        MISSING_PENDING,
        WRONG_PENDING_TYPE
    }
}
