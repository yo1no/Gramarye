package com.yo1no.gramarye.magic.definition.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Test-only streaming SHA-256 helpers; successful output exposes only bounded hex witnesses. */
final class P4B2Hashing {
    private static final int BUFFER_BYTES = 8_192;

    private P4B2Hashing() {
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

    static String sha256(EncodedSkillStoreCarrier carrier) {
        var bytes = new byte[carrier.storeByteCount()];
        carrier.copyStoreBlobInto(bytes, 0);
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
