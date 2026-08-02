package com.yo1no.gramarye.magic.definition.research;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.nbt.Tag;

/** Streaming hashes used only to identify deterministic synthetic research fixtures. */
final class P4E0ResearchHashing {
    private static final int BUFFER_BYTES = 8_192;

    private P4E0ResearchHashing() {
    }

    static String sha256(Path path) throws IOException {
        var digest = sha256();
        try (InputStream input = Files.newInputStream(path)) {
            var buffer = new byte[BUFFER_BYTES];
            for (var read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                if (read != 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(String value) {
        var digest = sha256();
        return HexFormat.of().formatHex(
                digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    static String semanticTagChecksum(Tag tag) {
        return sha256(tag.toString());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by Java", exception);
        }
    }
}
