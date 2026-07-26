package com.yo1no.gramarye.magic.definition.store;

import com.yo1no.gramarye.magic.limits.MagicSafetyCeilings;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/** Shared bounded cursor used before any schema-specific NBT materialization. */
final class StrictNbtFramingInput {
    private static final long QUOTA_WIRE_MULTIPLIER = 2;
    private static final long QUOTA_NODE_BYTES = 65;

    private final byte[] bytes;
    private final NbtAccounter accounter;
    private final Long materializationQuota;
    private int position;
    private int physicalNodeCount;

    StrictNbtFramingInput(byte[] bytes) {
        this(bytes, null);
    }

    StrictNbtFramingInput(byte[] bytes, long materializationQuota) {
        this(bytes, Long.valueOf(materializationQuota));
        if (materializationQuota <= 0) {
            throw new IllegalArgumentException("materializationQuota must be positive");
        }
    }

    private StrictNbtFramingInput(byte[] bytes, Long materializationQuota) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        if (bytes.length == 0) {
            throw new IllegalArgumentException("encoded NBT must not be empty");
        }
        this.accounter = new NbtAccounter(
                accounterQuota(bytes.length), MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH);
        this.materializationQuota = materializationQuota;
    }

    static long accounterQuota(long encodedLength) {
        if (encodedLength <= 0) {
            throw new IllegalArgumentException("encodedLength must be positive");
        }
        return Math.addExact(
                Math.multiplyExact(QUOTA_WIRE_MULTIPLIER, encodedLength),
                Math.multiplyExact(
                        QUOTA_NODE_BYTES, MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES));
    }

    void requireAnyRootCompound() throws MalformedNbtException {
        requireCompoundType();
    }

    void requireUnnamedRootCompound() throws MalformedNbtException {
        requireCompoundType();
        if (readUnsignedByte() != 0 || readUnsignedByte() != 0) {
            throw new MalformedNbtException();
        }
    }

    private void requireCompoundType() throws MalformedNbtException {
        if (readUnsignedByte() != Tag.TAG_COMPOUND) {
            throw new MalformedNbtException();
        }
        accountNode();
    }

    boolean atCompoundEnd() throws MalformedNbtException {
        requireRemaining(1);
        if (Byte.toUnsignedInt(bytes[position]) != Tag.TAG_END) {
            return false;
        }
        consume(1);
        return true;
    }

    NamedField readNamedField() throws MalformedNbtException {
        var field = new NamedField(readUnsignedByte(), readUtf());
        accountNode();
        return field;
    }

    int readInt() throws MalformedNbtException {
        requireRemaining(4);
        var value = (Byte.toUnsignedInt(bytes[position]) << 24)
                | (Byte.toUnsignedInt(bytes[position + 1]) << 16)
                | (Byte.toUnsignedInt(bytes[position + 2]) << 8)
                | Byte.toUnsignedInt(bytes[position + 3]);
        consume(4);
        return value;
    }

    int readLength() throws MalformedNbtException {
        var length = readInt();
        if (length < 0) {
            throw new MalformedNbtException();
        }
        return length;
    }

    String readUtf() throws MalformedNbtException {
        requireRemaining(2);
        var length = (Byte.toUnsignedInt(bytes[position]) << 8)
                | Byte.toUnsignedInt(bytes[position + 1]);
        consume(2);
        if (length > MagicSafetyCeilings.MAX_STRING_LENGTH) {
            throw new MalformedNbtException();
        }
        return new String(readBytes(length), StandardCharsets.UTF_8);
    }

    int[] readUuidArray() throws MalformedNbtException {
        if (readLength() != 4) {
            throw new MalformedNbtException();
        }
        return new int[] {readInt(), readInt(), readInt(), readInt()};
    }

    BlobListPreflight preflightBlobList(int nestedMaximum, NestedCapacityFactory capacityFactory)
            throws MalformedNbtException {
        Objects.requireNonNull(capacityFactory, "capacityFactory");
        if (nestedMaximum <= 0 || readUnsignedByte() != Tag.TAG_BYTE_ARRAY) {
            throw new MalformedNbtException();
        }
        var count = readInt();
        if (count < 0 || count > remaining() / 4) {
            throw new MalformedNbtException();
        }
        var slices = new ArrayList<ByteSlice>();
        StorePersistenceFailure deferred = null;
        for (var index = 0; index < count; index++) {
            accountNode();
            var length = readLength();
            if (length > nestedMaximum && deferred == null) {
                deferred = capacityFactory.create(length);
            }
            slices.add(preflightSlice(length));
        }
        return new BlobListPreflight(slices, java.util.Optional.ofNullable(deferred));
    }

    ByteSlice preflightSlice(int length) throws MalformedNbtException {
        requireRemaining(length);
        var result = new ByteSlice(position, length);
        consume(length);
        return result;
    }

    byte[] copySlice(ByteSlice slice) throws MalformedNbtException {
        Objects.requireNonNull(slice, "slice");
        if (slice.offset() > bytes.length || slice.length() > bytes.length - slice.offset()) {
            throw new MalformedNbtException();
        }
        return java.util.Arrays.copyOfRange(bytes, slice.offset(), slice.offset() + slice.length());
    }

    byte[] readBytes(int length) throws MalformedNbtException {
        requireRemaining(length);
        var result = java.util.Arrays.copyOfRange(bytes, position, position + length);
        consume(length);
        return result;
    }

    int position() {
        return position;
    }

    void requireFinished() throws MalformedNbtException {
        if (position != bytes.length) {
            throw new MalformedNbtException();
        }
    }

    void verifyFiniteMaterializationQuota() throws MalformedNbtException {
        if (materializationQuota == null) {
            throw new IllegalStateException("no finite materialization quota was configured");
        }
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            NbtIo.read(
                    input,
                    new NbtAccounter(
                            materializationQuota,
                            MagicSafetyCeilings.MAX_SKILL_DOCUMENT_DEPTH));
            if (input.read() != -1) {
                throw new MalformedNbtException();
            }
        } catch (IOException | RuntimeException exception) {
            throw new MalformedNbtException();
        }
    }

    private int readUnsignedByte() throws MalformedNbtException {
        requireRemaining(1);
        var value = Byte.toUnsignedInt(bytes[position]);
        consume(1);
        return value;
    }

    private int remaining() {
        return bytes.length - position;
    }

    private void requireRemaining(int length) throws MalformedNbtException {
        if (length < 0 || length > remaining()) {
            throw new MalformedNbtException();
        }
    }

    private void consume(int length) {
        accounter.accountBytes(length);
        position += length;
    }

    private void accountNode() throws MalformedNbtException {
        if (physicalNodeCount >= MagicSafetyCeilings.MAX_SKILL_DOCUMENT_TREE_NODES) {
            throw new MalformedNbtException();
        }
        physicalNodeCount++;
        accounter.accountBytes(QUOTA_NODE_BYTES);
    }

    record NamedField(int type, String name) {
        NamedField {
            Objects.requireNonNull(name, "name");
        }
    }

    record ByteSlice(int offset, int length) {
        ByteSlice {
            if (offset < 0 || length < 0) {
                throw new IllegalArgumentException("slice bounds must be non-negative");
            }
        }
    }

    record BlobListPreflight(
            List<ByteSlice> slices,
            java.util.Optional<StorePersistenceFailure> deferredCapacity) {
        BlobListPreflight {
            slices = List.copyOf(Objects.requireNonNull(slices, "slices"));
            Objects.requireNonNull(deferredCapacity, "deferredCapacity");
        }
    }

    @FunctionalInterface
    interface NestedCapacityFactory {
        StorePersistenceFailure create(long observedAtLeast);
    }

    static final class MalformedNbtException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
