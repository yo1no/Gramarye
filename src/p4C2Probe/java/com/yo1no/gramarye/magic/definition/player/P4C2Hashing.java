package com.yo1no.gramarye.magic.definition.player;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.DigestOutputStream;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Streaming SHA-256 utilities whose output is always a bounded lowercase witness. */
final class P4C2Hashing {
    private static final int BUFFER_BYTES = 8_192;

    private P4C2Hashing() {
    }

    static String sha256(Path path) throws IOException {
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

    static String sha256(byte[] bytes) {
        return hex(digest().digest(bytes));
    }

    static String payloadSha256(ByteArrayTag tag) {
        return sha256(tag.getAsByteArray());
    }

    /** Canonical logical-tree checksum; Compound field order is intentionally not semantic. */
    static String sha256(Tag tag) {
        var digest = digest();
        try (var output = new DataOutputStream(new DigestOutputStream(
                OutputStream.nullOutputStream(), digest))) {
            output.writeByte(tag.getId());
            writeCanonicalPayload(tag, output);
        } catch (IOException exception) {
            throw new AssertionError("in-memory NBT hashing failed", exception);
        }
        return hex(digest.digest());
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

    static String uuidChecksum(UUID playerId) {
        var bytes = new byte[Long.BYTES * 2];
        putLong(bytes, 0, playerId.getMostSignificantBits());
        putLong(bytes, Long.BYTES, playerId.getLeastSignificantBits());
        return sha256(bytes);
    }

    static String witness(String checksum) {
        requireSha256(checksum);
        return checksum.substring(0, 16);
    }

    static void requireSha256(String checksum) {
        if (checksum == null || !checksum.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("checksum must be 64 lowercase hex characters");
        }
    }

    private static void putLong(byte[] output, int offset, long value) {
        for (var index = 0; index < Long.BYTES; index++) {
            output[offset + index] = (byte) (value >>> (56 - index * 8));
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

}
