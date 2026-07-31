package com.yo1no.gramarye.magic.definition.store;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Streaming and logical-tree SHA-256 witnesses for the isolated P4-D3 gate. */
public final class P4D3Hashing {
    private static final int BUFFER_BYTES = 8_192;

    private P4D3Hashing() {
    }

    public static String sha256(Path path) throws IOException {
        var digest = digest();
        var buffer = new byte[BUFFER_BYTES];
        try (InputStream input = Files.newInputStream(path)) {
            for (int count; (count = input.read(buffer)) != -1; ) {
                if (count > 0) {
                    digest.update(buffer, 0, count);
                }
            }
        }
        return hex(digest.digest());
    }

    public static String sha256(byte[] bytes) {
        return hex(digest().digest(bytes));
    }

    /** Canonical logical-tree checksum; Compound insertion order is not semantic. */
    public static String sha256(Tag tag) {
        var digest = digest();
        try (var output = new DataOutputStream(new DigestOutputStream(
                OutputStream.nullOutputStream(), digest))) {
            output.writeByte(tag.getId());
            writeCanonicalPayload(tag, output);
        } catch (IOException exception) {
            throw new AssertionError("in-memory P4-D3 NBT hashing failed", exception);
        }
        return hex(digest.digest());
    }

    static String sha256(EncodedSkillStoreCarrier carrier) {
        var digest = digest();
        var maximumHistoryBytes = carrier.histories().stream()
                .mapToInt(EncodedHistoryIndex::byteLength)
                .max()
                .orElse(0);
        var scratch = new byte[maximumHistoryBytes];
        var output = new DigestFraming(digest);
        output.writeByte(Tag.TAG_COMPOUND);
        output.writeNamedInt(
                "store_schema_version",
                StorePersistenceSchema.CURRENT_SCHEMA_VERSION);
        output.writeByte(Tag.TAG_LIST);
        output.writeUtf("history_entries");
        output.writeByte(Tag.TAG_BYTE_ARRAY);
        output.writeInt(carrier.historyCount());
        for (var history : carrier.histories()) {
            output.writeInt(history.byteLength());
            carrier.historySlice(history).copyInto(scratch, 0);
            output.writeBytes(scratch, history.byteLength());
        }
        output.writeByte(Tag.TAG_END);
        if (output.byteCount != carrier.storeByteCount()) {
            throw new AssertionError("P4-D3 Store digest framing length changed");
        }
        return hex(digest.digest());
    }

    public static String uuid(UUID value) {
        var bytes = new byte[Long.BYTES * 2];
        putLong(bytes, 0, value.getMostSignificantBits());
        putLong(bytes, Long.BYTES, value.getLeastSignificantBits());
        return sha256(bytes);
    }

    public static String witness(String checksum) {
        requireSha256(checksum);
        return checksum.substring(0, 16);
    }

    public static void requireSha256(String checksum) {
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksum must be 64 lowercase hex characters");
        }
    }

    private static void writeCanonicalPayload(Tag tag, DataOutputStream output)
            throws IOException {
        if (tag instanceof CompoundTag compound) {
            var names = new ArrayList<>(compound.getAllKeys());
            names.sort(String::compareTo);
            for (var name : names) {
                var child = compound.get(name);
                if (child == null) {
                    throw new AssertionError("Compound key lost its value");
                }
                output.writeByte(child.getId());
                output.writeUTF(name);
                writeCanonicalPayload(child, output);
            }
            output.writeByte(Tag.TAG_END);
        } else if (tag instanceof ListTag list) {
            output.writeByte(list.getElementType());
            output.writeInt(list.size());
            for (var child : list) {
                writeCanonicalPayload(child, output);
            }
        } else {
            tag.write(output);
        }
    }

    private static void putLong(byte[] bytes, int offset, long value) {
        for (var index = 0; index < Long.BYTES; index++) {
            bytes[offset + index] = (byte) (value >>> (56 - index * 8));
        }
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is unavailable", exception);
        }
    }

    private static String hex(byte[] bytes) {
        var result = new StringBuilder(bytes.length * 2);
        for (var value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xF, 16));
            result.append(Character.forDigit(value & 0xF, 16));
        }
        return result.toString();
    }

    private static final class DigestFraming {
        private final MessageDigest digest;
        private long byteCount;

        private DigestFraming(MessageDigest digest) {
            this.digest = digest;
        }

        private void writeNamedInt(String name, int value) {
            writeByte(Tag.TAG_INT);
            writeUtf(name);
            writeInt(value);
        }

        private void writeUtf(String value) {
            var bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > 65_535) {
                throw new IllegalArgumentException("NBT name exceeds unsigned-short framing");
            }
            writeByte(bytes.length >>> 8);
            writeByte(bytes.length);
            writeBytes(bytes, bytes.length);
        }

        private void writeInt(int value) {
            writeByte(value >>> 24);
            writeByte(value >>> 16);
            writeByte(value >>> 8);
            writeByte(value);
        }

        private void writeByte(int value) {
            digest.update((byte) value);
            byteCount++;
        }

        private void writeBytes(byte[] bytes, int length) {
            digest.update(bytes, 0, length);
            byteCount = Math.addExact(byteCount, length);
        }
    }
}
