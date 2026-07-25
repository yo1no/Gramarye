package com.yo1no.gramarye.magic.definition.store;

import java.nio.charset.StandardCharsets;

/** Byte-exact arbitrary-NBT fixtures shared only by P4-B1 tests. */
final class SkillSavedDataTestSupport {
    private SkillSavedDataTestSupport() {
    }

    static byte[] canonicalWholeRoot(byte[] store, byte[] pending) {
        return canonicalWholeRoot(0, store, pending);
    }

    static byte[] canonicalWholeRoot(int schemaVersion, byte[] store, byte[] pending) {
        var innerSize = Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(store.length, pending.length));
        var whole = new byte[Math.addExact(
                innerSize,
                SkillSavedDataPersistenceSchema.WHOLE_ROOT_V0_FRAMING_OVERHEAD)];
        var cursor = 0;
        whole[cursor++] = net.minecraft.nbt.Tag.TAG_COMPOUND;
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_COMPOUND,
                SkillSavedDataPersistenceSchema.DATA_FIELD);
        cursor = writeInnerPayload(whole, cursor, schemaVersion, store, pending);
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_INT,
                SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD);
        cursor = writeInt(whole, cursor, 3_958);
        whole[cursor++] = net.minecraft.nbt.Tag.TAG_END;
        if (cursor != whole.length) {
            throw new AssertionError("whole-root fixture size mismatch");
        }
        return whole;
    }

    static byte[] canonicalWholeRootWithZeroPayloads(
            int schemaVersion,
            int storeLength,
            int pendingLength) {
        if (storeLength < 0 || pendingLength < 0) {
            throw new IllegalArgumentException("payload lengths must be non-negative");
        }
        var innerSize = Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(storeLength, pendingLength));
        var whole = new byte[Math.addExact(
                innerSize,
                SkillSavedDataPersistenceSchema.WHOLE_ROOT_V0_FRAMING_OVERHEAD)];
        var cursor = 0;
        whole[cursor++] = net.minecraft.nbt.Tag.TAG_COMPOUND;
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_COMPOUND,
                SkillSavedDataPersistenceSchema.DATA_FIELD);
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_INT,
                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD);
        cursor = writeInt(whole, cursor, schemaVersion);
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_BYTE_ARRAY,
                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD);
        cursor = writeInt(whole, cursor, storeLength);
        cursor += storeLength;
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_BYTE_ARRAY,
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD);
        cursor = writeInt(whole, cursor, pendingLength);
        cursor += pendingLength;
        whole[cursor++] = net.minecraft.nbt.Tag.TAG_END;
        cursor = writeHeader(
                whole,
                cursor,
                net.minecraft.nbt.Tag.TAG_INT,
                SkillSavedDataPersistenceSchema.DATA_VERSION_FIELD);
        cursor = writeInt(whole, cursor, 3_958);
        whole[cursor++] = net.minecraft.nbt.Tag.TAG_END;
        if (cursor != whole.length) {
            throw new AssertionError("large whole-root fixture size mismatch");
        }
        return whole;
    }

    static byte[] standaloneInner(byte[] store, byte[] pending) {
        var inner = new byte[Math.addExact(
                SkillSavedDataPersistenceSchema.INNER_CARRIER_V0_FRAMING_BYTES,
                Math.addExact(store.length, pending.length))];
        inner[0] = net.minecraft.nbt.Tag.TAG_COMPOUND;
        var cursor = writeInnerPayload(inner, 1, 0, store, pending);
        if (cursor != inner.length) {
            throw new AssertionError("inner-carrier fixture size mismatch");
        }
        return inner;
    }

    static byte[] canonicalEmptyStoreBlob() {
        var encoded = SkillDefinitionStorePersistenceBridge.encodeCurrentStoreBlob(
                new SkillDefinitionStore());
        if (encoded instanceof StorePersistenceEncodeResult.Success success) {
            return success.blob().copyBytes();
        }
        throw new AssertionError("empty Store encode failed");
    }

    private static int writeInnerPayload(
            byte[] target,
            int cursor,
            int schemaVersion,
            byte[] store,
            byte[] pending) {
        cursor = writeHeader(
                target,
                cursor,
                net.minecraft.nbt.Tag.TAG_INT,
                SkillSavedDataPersistenceSchema.SCHEMA_VERSION_FIELD);
        cursor = writeInt(target, cursor, schemaVersion);
        cursor = writeHeader(
                target,
                cursor,
                net.minecraft.nbt.Tag.TAG_BYTE_ARRAY,
                SkillSavedDataPersistenceSchema.STORE_BLOB_FIELD);
        cursor = writeInt(target, cursor, store.length);
        System.arraycopy(store, 0, target, cursor, store.length);
        cursor += store.length;
        cursor = writeHeader(
                target,
                cursor,
                net.minecraft.nbt.Tag.TAG_BYTE_ARRAY,
                SkillSavedDataPersistenceSchema.PENDING_UPDATES_BLOB_FIELD);
        cursor = writeInt(target, cursor, pending.length);
        System.arraycopy(pending, 0, target, cursor, pending.length);
        cursor += pending.length;
        target[cursor++] = net.minecraft.nbt.Tag.TAG_END;
        return cursor;
    }

    private static int writeHeader(byte[] target, int cursor, int type, String name) {
        var encoded = name.getBytes(StandardCharsets.UTF_8);
        target[cursor++] = (byte) type;
        target[cursor++] = (byte) (encoded.length >>> 8);
        target[cursor++] = (byte) encoded.length;
        System.arraycopy(encoded, 0, target, cursor, encoded.length);
        return cursor + encoded.length;
    }

    private static int writeInt(byte[] target, int cursor, int value) {
        target[cursor++] = (byte) (value >>> 24);
        target[cursor++] = (byte) (value >>> 16);
        target[cursor++] = (byte) (value >>> 8);
        target[cursor++] = (byte) value;
        return cursor;
    }
}
